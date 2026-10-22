#!/usr/bin/env python3
"""Publishes the Google Play store listing from store-listing/google-play/: the texts, the icon,
the feature graphic and the phone and tablet screenshots of every <locale>/ folder, and the app's
contact details.

    pip install google-api-python-client google-auth-httplib2
    PLAY_SERVICE_ACCOUNT_JSON="$(cat key.json)" python3 scripts/play_listing.py [--validate-only]

Images are replaced, not added to: each image type of a language is cleared and uploaded again
from the files in the folder, in file-name order. --contact-from copies the contact email from
another app on the same developer account (the email is public on the listing, but kept out of
this repository).

Uses the same service account as scripts/play_upload.py; the app's setup tasks in Play Console
(content rating, data safety, target audience, the foreground-service declaration) have no API
and are listed in store-listing/google-play/README.md.
"""
import argparse
import json
import os
import sys
from pathlib import Path

import google_auth_httplib2
import httplib2
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

PACKAGE_NAME = "com.sereneflow.timer"
LISTING_DIR = Path(__file__).resolve().parent.parent / "store-listing" / "google-play"
WEBSITE = "https://daniil-pastuhov.github.io/sereneflow-privacy/support.html"
# Play's limits per language.
LIMITS = {"title.txt": 30, "short-description.txt": 80, "full-description.txt": 4000}
# Image type -> file or folder under the listing (per language unless the path starts with "/").
IMAGES = {
    "icon": "/icon-512.png",
    "featureGraphic": "feature-graphic.png",
    "phoneScreenshots": "phone-screenshots",
    "tenInchScreenshots": "tablet-screenshots",
}


def read_text(locale_dir, name):
    text = (locale_dir / name).read_text(encoding="utf-8").strip()
    if len(text) > LIMITS[name]:
        sys.exit(f"{locale_dir.name}/{name} has {len(text)} characters; Play allows {LIMITS[name]}")
    return text


def image_files(locale_dir, path):
    target = LISTING_DIR / path[1:] if path.startswith("/") else locale_dir / path
    if target.is_dir():
        return sorted(target.glob("*.png"), key=lambda p: (len(p.stem), p.stem))
    return [target] if target.exists() else []


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--contact-from", metavar="PACKAGE",
                        help="copy the contact email from this app's Play details")
    parser.add_argument("--validate-only", action="store_true",
                        help="run every step, then discard the edit instead of committing it")
    args = parser.parse_args()

    key = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not key:
        sys.exit("PLAY_SERVICE_ACCOUNT_JSON is not set")
    credentials = service_account.Credentials.from_service_account_info(
        json.loads(key), scopes=["https://www.googleapis.com/auth/androidpublisher"])
    http = google_auth_httplib2.AuthorizedHttp(credentials, http=httplib2.Http(timeout=300))
    edits = build("androidpublisher", "v3", http=http, cache_discovery=False).edits()

    contact_email = None
    if args.contact_from:
        other = edits.insert(packageName=args.contact_from, body={}).execute()["id"]
        try:
            contact_email = edits.details().get(packageName=args.contact_from, editId=other) \
                .execute().get("contactEmail")
        finally:
            edits.delete(packageName=args.contact_from, editId=other).execute()
        if not contact_email:
            sys.exit(f"{args.contact_from} has no contact email to copy")

    locales = sorted(d for d in LISTING_DIR.iterdir() if d.is_dir() and (d / "title.txt").exists())
    if not locales:
        sys.exit(f"No <locale>/title.txt under {LISTING_DIR}")

    edit_id = edits.insert(packageName=PACKAGE_NAME, body={}).execute()["id"]
    try:
        details = edits.details().get(packageName=PACKAGE_NAME, editId=edit_id).execute()
        details.update({"defaultLanguage": locales[0].name, "contactWebsite": WEBSITE})
        if contact_email:
            details["contactEmail"] = contact_email
        edits.details().update(packageName=PACKAGE_NAME, editId=edit_id, body=details).execute()
        print(f"Set contact details (default language {locales[0].name})")

        for locale_dir in locales:
            language = locale_dir.name
            edits.listings().update(
                packageName=PACKAGE_NAME, editId=edit_id, language=language,
                body={"language": language,
                      "title": read_text(locale_dir, "title.txt"),
                      "shortDescription": read_text(locale_dir, "short-description.txt"),
                      "fullDescription": read_text(locale_dir, "full-description.txt")},
            ).execute()
            print(f"{language}: texts")
            for image_type, path in IMAGES.items():
                files = image_files(locale_dir, path)
                if not files:
                    print(f"{language}: no {image_type}, left as is")
                    continue
                edits.images().deleteall(packageName=PACKAGE_NAME, editId=edit_id,
                                         language=language, imageType=image_type).execute()
                for file in files:
                    edits.images().upload(
                        packageName=PACKAGE_NAME, editId=edit_id, language=language,
                        imageType=image_type, media_body=MediaFileUpload(str(file), mimetype="image/png"),
                    ).execute()
                print(f"{language}: {image_type} ({len(files)})")

        if args.validate_only:
            edits.validate(packageName=PACKAGE_NAME, editId=edit_id).execute()
            edits.delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
            print("Validated; edit discarded")
        else:
            edits.commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
            print("Listing published")
        return 0
    except (HttpError, SystemExit) as error:
        edits.delete(packageName=PACKAGE_NAME, editId=edit_id).execute()
        if isinstance(error, SystemExit):
            raise
        print(f"Google Play rejected the listing: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
