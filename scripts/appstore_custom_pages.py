#!/usr/bin/env python3
"""Creates or updates the App Store's custom product pages from
store-listing/app-store/custom-pages/<page>/ through the App Store Connect API: each page's
promotional text, screenshots and the search keywords it is shown for.

    source ~/.config/sereneflow/store.env
    python3 scripts/appstore_custom_pages.py [--submit] [page ...]

A page folder holds page.json ("name": the page's reference name in App Store Connect, "keywords",
and the "shots" and "captions" scripts/frame_screenshots.py --page frames into
en-US/screenshots/) and en-US/promotional_text.txt. Screenshot sets the page has no folder for
are uploaded from the default listing (store-listing/app-store/en-US/), so only the iPhone set
differs.

A page can only be shown for keywords of the version that is live, so a keyword that is not
among them yet is skipped with a warning: run this again once the version that adds it is live.
Without --submit the pages stay drafts; with it, each page whose version is still in preparation
is sent to App Review. Reads the same ASC_* variables as scripts/testflight.py.
"""
import argparse
import json
import sys
from pathlib import Path

from appstore_listing import LISTING_DIR, PRIMARY_LOCALE, SCREENSHOT_SETS, patch, upload_screenshot, wait_for_assets
from testflight import Asc, AscError

PAGES_DIR = LISTING_DIR / "custom-pages"
PROMOTIONAL_TEXT_LIMIT = 170
# Versions whose texts and screenshots can still be changed.
DRAFT_STATES = {"PREPARE_FOR_SUBMISSION", "REJECTED"}


def create_page(asc, app_id, name, promotional_text):
    """A new page with its first version and en-US localization, which must be created together."""
    return asc.call("POST", "/appCustomProductPages", body={
        "data": {"type": "appCustomProductPages", "attributes": {"name": name}, "relationships": {
            "app": {"data": {"type": "apps", "id": app_id}},
            "appCustomProductPageVersions": {"data": [
                {"type": "appCustomProductPageVersions", "id": "${version}"}]}}},
        "included": [
            {"type": "appCustomProductPageVersions", "id": "${version}", "relationships": {
                "appCustomProductPageLocalizations": {"data": [
                    {"type": "appCustomProductPageLocalizations", "id": "${localization}"}]}}},
            {"type": "appCustomProductPageLocalizations", "id": "${localization}",
             "attributes": {"locale": PRIMARY_LOCALE, "promotionalText": promotional_text}}]})["data"]


def draft_version(asc, page_id):
    """The page's version that can be edited; a page whose versions are all live or in review gets a new one."""
    versions = asc.call("GET", f"/appCustomProductPages/{page_id}/appCustomProductPageVersions")["data"]
    draft = next((v for v in versions if v["attributes"]["state"] in DRAFT_STATES), None)
    if draft:
        return draft
    if any(v["attributes"]["state"] in ("READY_FOR_REVIEW", "WAITING_FOR_REVIEW", "IN_REVIEW") for v in versions):
        return None
    return asc.call("POST", "/appCustomProductPageVersions", body={"data": {
        "type": "appCustomProductPageVersions", "relationships": {"appCustomProductPage": {
            "data": {"type": "appCustomProductPages", "id": page_id}}}}})["data"]


def localization(asc, version_id, promotional_text):
    existing = next((loc for loc in asc.call(
        "GET", f"/appCustomProductPageVersions/{version_id}/appCustomProductPageLocalizations")["data"]
        if loc["attributes"]["locale"] == PRIMARY_LOCALE), None)
    if existing:
        patch(asc, "appCustomProductPageLocalizations", existing["id"], {"promotionalText": promotional_text})
        return existing["id"]
    return asc.call("POST", "/appCustomProductPageLocalizations", body={"data": {
        "type": "appCustomProductPageLocalizations",
        "attributes": {"locale": PRIMARY_LOCALE, "promotionalText": promotional_text},
        "relationships": {"appCustomProductPageVersion": {"data": {
            "type": "appCustomProductPageVersions", "id": version_id}}}}})["data"]["id"]


def screenshots(asc, localization_id, page_dir):
    """Replaces every screenshot set, from the page's folder or else the default listing's."""
    sets = {s["attributes"]["screenshotDisplayType"]: s["id"] for s in asc.call(
        "GET", f"/appCustomProductPageLocalizations/{localization_id}/appScreenshotSets")["data"]}
    for folder, display_type in SCREENSHOT_SETS.items():
        if display_type.startswith("APP_WATCH"):
            continue  # A custom page takes no Apple Watch screenshots ("Display Type Not Allowed").
        own = page_dir / PRIMARY_LOCALE / folder
        source = own if own.is_dir() else LISTING_DIR / PRIMARY_LOCALE / folder
        files = sorted(source.glob("*.png"), key=lambda p: (len(p.stem), p.stem))
        if not files:
            continue
        if display_type in sets:
            for old in asc.call("GET", f"/appScreenshotSets/{sets[display_type]}/appScreenshots")["data"]:
                asc.call("DELETE", f"/appScreenshots/{old['id']}")
            set_id = sets[display_type]
        else:
            set_id = asc.call("POST", "/appScreenshotSets", body={"data": {
                "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": display_type},
                "relationships": {"appCustomProductPageLocalization": {"data": {
                    "type": "appCustomProductPageLocalizations", "id": localization_id}}}}})["data"]["id"]
        ids = [upload_screenshot(asc, set_id, f) for f in files]
        wait_for_assets(asc, "appScreenshots", ids)
        print(f"  {display_type}: {len(ids)} screenshots ({'its own' if source == own else 'the default listing'})")


def keywords(asc, app_id, localization_id, wanted):
    """Sets the keywords the page is shown for to those of [wanted] the live version has."""
    live = {k["id"] for k in asc.call("GET", f"/apps/{app_id}/searchKeywords", {
        "filter[locale]": PRIMARY_LOCALE, "filter[platform]": "IOS", "limit": 200})["data"]}
    path = f"/appCustomProductPageLocalizations/{localization_id}/relationships/searchKeywords"
    current = {k["id"] for k in asc.call("GET", path, {"limit": 200})["data"]}
    usable = [k for k in wanted if k in live]
    missing = [k for k in wanted if k not in live]
    add, remove = [k for k in usable if k not in current], sorted(current - set(usable))
    if add:
        asc.call("POST", path, body={"data": [{"type": "appKeywords", "id": k} for k in add]})
    if remove:
        asc.call("DELETE", path, body={"data": [{"type": "appKeywords", "id": k} for k in remove]})
    print(f"  keywords: {', '.join(usable) or 'none'}")
    if missing:
        print(f"  ::warning::not keywords of the live version yet, so left out: {', '.join(missing)}")


def submit(asc, app_id, version_ids):
    submission = asc.call("POST", "/reviewSubmissions", body={"data": {
        "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
        "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]
    for version_id in version_ids:
        asc.call("POST", "/reviewSubmissionItems", body={"data": {
            "type": "reviewSubmissionItems", "relationships": {
                "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": submission["id"]}},
                "appCustomProductPageVersion": {"data": {
                    "type": "appCustomProductPageVersions", "id": version_id}}}}})
    patch(asc, "reviewSubmissions", submission["id"], {"submitted": True})
    print(f"Submitted {len(version_ids)} page(s) for App Review")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("pages", nargs="*", help="page folder names; all of them by default")
    parser.add_argument("--submit", action="store_true", help="send the pages to App Review")
    args = parser.parse_args()

    page_dirs = sorted(d for d in PAGES_DIR.iterdir() if (d / "page.json").exists()
                       and (not args.pages or d.name in args.pages))
    if not page_dirs or len(page_dirs) < len(args.pages):
        sys.exit(f"No such page under {PAGES_DIR}")
    asc = Asc()
    app_id = asc.app_id()
    try:
        existing = {p["attributes"]["name"]: p for p in asc.call(
            "GET", f"/apps/{app_id}/appCustomProductPages", {"limit": 50})["data"]}
        to_submit = []
        for page_dir in page_dirs:
            spec = json.loads((page_dir / "page.json").read_text(encoding="utf-8"))
            text = (page_dir / PRIMARY_LOCALE / "promotional_text.txt").read_text(encoding="utf-8").strip()
            if len(text) > PROMOTIONAL_TEXT_LIMIT:
                sys.exit(f"{page_dir.name}: the promotional text has {len(text)} characters; "
                         f"the limit is {PROMOTIONAL_TEXT_LIMIT}")
            page = existing.get(spec["name"])
            print(f"{spec['name']}: {'updating' if page else 'creating'}")
            page = page or create_page(asc, app_id, spec["name"], text)
            version = draft_version(asc, page["id"])
            if not version:
                print("  in App Review; left as it is")
                continue
            localization_id = localization(asc, version["id"], text)
            screenshots(asc, localization_id, page_dir)
            keywords(asc, app_id, localization_id, spec["keywords"])
            to_submit.append(version["id"])
        if args.submit and to_submit:
            submit(asc, app_id, to_submit)
    except AscError as error:
        sys.exit(f"App Store Connect API error: {error}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
