#!/usr/bin/env python3
"""Fills in the App Store listing of the editable App Store version from store-listing/app-store/
through the App Store Connect API: app information (name, subtitle, privacy policy, categories,
content rights, age rating), availability and price (free, everywhere), the version's texts and
copyright, the App Review contact and notes, and the iPhone 6.9" and iPad 13" screenshots.

    source ~/.config/sereneflow/store.env
    python3 scripts/appstore_listing.py --version 1.0.0 --review-contact-from com.foodbest.ios

Run it before `scripts/testflight.py appstore`, which attaches the build and submits. Reads the
same ASC_* variables. The parts without an API are listed in store-listing/app-store/README.md:
App Privacy, the iPhone Duo screenshots and the Header and Search Results images.

--review-contact-from copies the App Review contact (name, phone, email) from another app's newest
version on the same account, so the personal details stay out of this repository.
"""
import argparse
import hashlib
import sys
import time
import urllib.request
from pathlib import Path

from testflight import EDITABLE_STATES, Asc, AscError, version_state

BUNDLE_ID = "com.sereneflow.timer"
ROOT = Path(__file__).resolve().parent.parent
LISTING_DIR = ROOT / "store-listing" / "app-store"
PRIVACY_URL = "https://daniil-pastuhov.github.io/sereneflow-privacy/"
SUPPORT_URL = "https://daniil-pastuhov.github.io/sereneflow-privacy/support.html"
COPYRIGHT = "2026 Daniil Pastuhov"
PRIMARY_CATEGORY, SECONDARY_CATEGORY = "HEALTH_AND_FITNESS", "PRODUCTIVITY"
# Folder under <locale>/ -> App Store Connect display type. 1320x2868 iPhone screenshots go in
# the 6.7" slot, which App Store Connect labels 6.9" and also uses for smaller iPhones.
SCREENSHOT_SETS = {"screenshots": "APP_IPHONE_67", "ipad-screenshots": "APP_IPAD_PRO_3GEN_129"}
LIMITS = {"name.txt": 30, "subtitle.txt": 30, "promotional_text.txt": 170, "keywords.txt": 100,
          "description.txt": 4000}
# Every age-rating question answered No / None, except that the routines are about wellness
# (breathing, meditation). The result is 4+.
AGE_RATING = {
    "advertising": False, "ageAssurance": False, "alcoholTobaccoOrDrugUseOrReferences": "NONE",
    "contests": "NONE", "gambling": False, "gamblingSimulated": "NONE",
    "gunsOrOtherWeapons": "NONE", "healthOrWellnessTopics": True, "horrorOrFearThemes": "NONE",
    "lootBox": False, "matureOrSuggestiveThemes": "NONE", "medicalOrTreatmentInformation": "NONE",
    "messagingAndChat": False, "parentalControls": False, "profanityOrCrudeHumor": "NONE",
    "sexualContentGraphicAndNudity": "NONE", "sexualContentOrNudity": "NONE",
    "socialMedia": False, "socialMediaAgeRestricted": False, "unrestrictedWebAccess": False,
    "userGeneratedContent": False, "violenceCartoonOrFantasy": "NONE",
    "violenceRealistic": "NONE", "violenceRealisticProlongedGraphicOrSadistic": "NONE",
}
REVIEW_NOTES = """SereneFlow has no account or login, and no network access; everything stays on the device.

To try it: tap "Begin Routine" on "Morning Awakening". Each step counts down with an animated visual timer, and a voice announces the steps (turn the volume up). Use "New Routine" to build one, and the gear button for themes, default sounds and reminders.

Background audio: with "Follow routine with screen off" on (Settings, or the prompt after saving a routine), the app keeps its audio session active while a routine runs, so spoken step cues and chimes play on time with the screen locked. A Live Activity shows the current step and time left. The audio session is released when the routine is paused or ends."""


def text(locale, name):
    value = (LISTING_DIR / locale / name).read_text(encoding="utf-8").strip()
    if len(value) > LIMITS[name]:
        sys.exit(f"{locale}/{name} has {len(value)} characters; the limit is {LIMITS[name]}")
    return value


def patch(asc, kind, item_id, attributes=None, relationships=None):
    data = {"type": kind, "id": item_id}
    if attributes:
        data["attributes"] = attributes
    if relationships:
        data["relationships"] = relationships
    return asc.call("PATCH", f"/{kind}/{item_id}", body={"data": data})


def app_info(asc, app_id, locales):
    info = next(i for i in asc.call("GET", f"/apps/{app_id}/appInfos")["data"]
                if (i["attributes"].get("state") or i["attributes"].get("appStoreState"))
                in EDITABLE_STATES | {"PREPARE_FOR_SUBMISSION"})
    patch(asc, "appInfos", info["id"], relationships={
        "primaryCategory": {"data": {"type": "appCategories", "id": PRIMARY_CATEGORY}},
        "secondaryCategory": {"data": {"type": "appCategories", "id": SECONDARY_CATEGORY}}})
    print(f"Categories: {PRIMARY_CATEGORY}, {SECONDARY_CATEGORY}")

    existing = {loc["attributes"]["locale"]: loc["id"] for loc in asc.call(
        "GET", f"/appInfos/{info['id']}/appInfoLocalizations")["data"]}
    for locale in locales:
        attributes = {"name": text(locale, "name.txt"), "subtitle": text(locale, "subtitle.txt"),
                      "privacyPolicyUrl": PRIVACY_URL}
        if locale in existing:
            patch(asc, "appInfoLocalizations", existing[locale], attributes)
        else:
            asc.call("POST", "/appInfoLocalizations", body={"data": {
                "type": "appInfoLocalizations", "attributes": {"locale": locale, **attributes},
                "relationships": {"appInfo": {"data": {"type": "appInfos", "id": info["id"]}}}}})
        print(f"{locale}: name, subtitle, privacy policy")

    rating = asc.call("GET", f"/appInfos/{info['id']}/ageRatingDeclaration")["data"]
    patch(asc, "ageRatingDeclarations", rating["id"], AGE_RATING)
    print("Age rating answered")


def availability_and_price(asc, app_id):
    try:
        asc.call("GET", f"/apps/{app_id}/appAvailabilityV2")
        print("Availability already set")
    except AscError as error:
        if error.status != 404:
            raise
        territories = [t["id"] for t in asc.call("GET", "/territories", {"limit": 200})["data"]]
        included = [{"type": "territoryAvailabilities", "id": f"${{t-{t}}}",
                     "attributes": {"available": True},
                     "relationships": {"territory": {"data": {"type": "territories", "id": t}}}}
                    for t in territories]
        asc.call("POST", "/v2/appAvailabilities", body={
            "data": {"type": "appAvailabilities", "attributes": {"availableInNewTerritories": True},
                     "relationships": {
                         "app": {"data": {"type": "apps", "id": app_id}},
                         "territoryAvailabilities": {"data": [
                             {"type": "territoryAvailabilities", "id": i["id"]} for i in included]}}},
            "included": included})
        print(f"Available in {len(territories)} territories and new ones")

    try:
        prices = asc.call("GET", f"/appPriceSchedules/{app_id}/manualPrices",
                          {"include": "appPricePoint", "limit": 5})
    except AscError as error:
        if error.status != 404:
            raise
        prices = {"data": []}
    if prices["data"]:
        points = {p["id"]: p["attributes"].get("customerPrice") for p in prices.get("included", [])
                  if p["type"] == "appPricePoints"}
        print(f"Price already set ({', '.join(str(v) for v in points.values()) or 'unknown'})")
        return
    free = next(p for p in asc.call("GET", f"/apps/{app_id}/appPricePoints",
                                    {"filter[territory]": "USA", "limit": 200})["data"]
                if float(p["attributes"]["customerPrice"]) == 0)
    asc.call("POST", "/appPriceSchedules", body={
        "data": {"type": "appPriceSchedules", "relationships": {
            "app": {"data": {"type": "apps", "id": app_id}},
            "baseTerritory": {"data": {"type": "territories", "id": "USA"}},
            "manualPrices": {"data": [{"type": "appPrices", "id": "${free}"}]}}},
        "included": [{"type": "appPrices", "id": "${free}", "attributes": {"startDate": None},
                      "relationships": {"appPricePoint": {"data": {
                          "type": "appPricePoints", "id": free["id"]}}}}]})
    print("Price: free")


def editable_version(asc, app_id, version_string):
    versions = asc.call("GET", f"/apps/{app_id}/appStoreVersions",
                        {"filter[platform]": "IOS", "limit": 50})["data"]
    version = next((v for v in versions if v["attributes"]["versionString"] == version_string), None) \
        or next((v for v in versions if version_state(v) in EDITABLE_STATES), None)
    if not version or version_state(version) not in EDITABLE_STATES:
        sys.exit(f"No editable App Store version for {version_string}")
    patch(asc, "appStoreVersions", version["id"],
          {"versionString": version_string, "copyright": COPYRIGHT})
    print(f"Version {version_string} ({version_state(version)}), copyright")
    return version


def version_texts(asc, version, locales):
    existing = {loc["attributes"]["locale"]: loc["id"] for loc in asc.call(
        "GET", f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"]}
    ids = {}
    for locale in locales:
        attributes = {"description": text(locale, "description.txt"),
                      "keywords": text(locale, "keywords.txt"),
                      "promotionalText": text(locale, "promotional_text.txt"),
                      "supportUrl": SUPPORT_URL}
        if locale in existing:
            patch(asc, "appStoreVersionLocalizations", existing[locale], attributes)
            ids[locale] = existing[locale]
        else:
            ids[locale] = asc.call("POST", "/appStoreVersionLocalizations", body={"data": {
                "type": "appStoreVersionLocalizations", "attributes": {"locale": locale, **attributes},
                "relationships": {"appStoreVersion": {"data": {
                    "type": "appStoreVersions", "id": version["id"]}}}}})["data"]["id"]
        print(f"{locale}: description, keywords, promotional text, support URL")
    return ids


def review_detail(asc, version, contact_from):
    attributes = {"notes": REVIEW_NOTES, "demoAccountRequired": False}
    if contact_from:
        other = asc.call("GET", "/apps", {"filter[bundleId]": contact_from})["data"][0]
        newest = asc.call("GET", f"/apps/{other['id']}/appStoreVersions", {"limit": 1})["data"][0]
        source = asc.call("GET", f"/appStoreVersions/{newest['id']}/appStoreReviewDetail")["data"]
        for key in ("contactFirstName", "contactLastName", "contactPhone", "contactEmail"):
            attributes[key] = source["attributes"][key]
    try:
        current = asc.call("GET", f"/appStoreVersions/{version['id']}/appStoreReviewDetail")["data"]
    except AscError as error:
        if error.status != 404:
            raise
        current = None
    if current:
        patch(asc, "appStoreReviewDetails", current["id"], attributes)
    else:
        asc.call("POST", "/appStoreReviewDetails", body={"data": {
            "type": "appStoreReviewDetails", "attributes": attributes,
            "relationships": {"appStoreVersion": {"data": {
                "type": "appStoreVersions", "id": version["id"]}}}}})
    print("App Review contact and notes" if contact_from else "App Review notes (no contact copied)")


def upload_screenshot(asc, set_id, path):
    data = path.read_bytes()
    shot = asc.call("POST", "/appScreenshots", body={"data": {
        "type": "appScreenshots", "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": set_id}}}}})["data"]
    for op in shot["attributes"]["uploadOperations"]:
        chunk = data[op["offset"]:op["offset"] + op["length"]]
        request = urllib.request.Request(op["url"], data=chunk, method=op["method"],
                                         headers={h["name"]: h["value"] for h in op["requestHeaders"]})
        with urllib.request.urlopen(request, timeout=120):
            pass
    patch(asc, "appScreenshots", shot["id"],
          {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()})
    return shot["id"]


def screenshots(asc, localization_ids):
    for locale, localization_id in localization_ids.items():
        sets = {s["attributes"]["screenshotDisplayType"]: s["id"] for s in asc.call(
            "GET", f"/appStoreVersionLocalizations/{localization_id}/appScreenshotSets")["data"]}
        for folder, display_type in SCREENSHOT_SETS.items():
            files = sorted((LISTING_DIR / locale / folder).glob("*.png"), key=lambda p: (len(p.stem), p.stem))
            if not files:
                continue
            if display_type in sets:
                # Replaced, not added to.
                for old in asc.call("GET", f"/appScreenshotSets/{sets[display_type]}/appScreenshots")["data"]:
                    asc.call("DELETE", f"/appScreenshots/{old['id']}")
                set_id = sets[display_type]
            else:
                set_id = asc.call("POST", "/appScreenshotSets", body={"data": {
                    "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": display_type},
                    "relationships": {"appStoreVersionLocalization": {"data": {
                        "type": "appStoreVersionLocalizations", "id": localization_id}}}}})["data"]["id"]
            ids = [upload_screenshot(asc, set_id, f) for f in files]
            wait_for_screenshots(asc, ids)
            print(f"{locale}: {display_type} ({len(ids)} screenshots)")


def wait_for_screenshots(asc, ids, timeout=900):
    deadline = time.time() + timeout
    pending = set(ids)
    while pending:
        for shot_id in list(pending):
            state = asc.call("GET", f"/appScreenshots/{shot_id}")["data"]["attributes"] \
                .get("assetDeliveryState") or {}
            if state.get("state") == "COMPLETE":
                pending.discard(shot_id)
            elif state.get("state") == "FAILED":
                sys.exit(f"Screenshot {shot_id} failed processing: {state.get('errors')}")
        if pending:
            if time.time() > deadline:
                sys.exit(f"{len(pending)} screenshots still processing after {timeout} s")
            time.sleep(5)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--version", required=True, help="marketing version, e.g. 1.0.0")
    parser.add_argument("--review-contact-from", metavar="BUNDLE_ID",
                        help="copy the App Review contact from this app's newest version")
    parser.add_argument("--skip-screenshots", action="store_true")
    args = parser.parse_args()

    locales = sorted(d.name for d in LISTING_DIR.iterdir() if (d / "name.txt").exists())
    asc = Asc()
    app_id = asc.app_id()
    try:
        patch(asc, "apps", app_id, {"contentRightsDeclaration": "DOES_NOT_USE_THIRD_PARTY_CONTENT"})
        print("Content rights: no third-party content")
        app_info(asc, app_id, locales)
        availability_and_price(asc, app_id)
        version = editable_version(asc, app_id, args.version)
        localization_ids = version_texts(asc, version, locales)
        review_detail(asc, version, args.review_contact_from)
        if not args.skip_screenshots:
            screenshots(asc, localization_ids)
    except AscError as error:
        sys.exit(f"App Store Connect API error: {error}")
    print("Listing filled in; next: scripts/testflight.py appstore")
    return 0


if __name__ == "__main__":
    sys.exit(main())
