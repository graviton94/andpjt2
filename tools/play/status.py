"""Print what Play has for the app: every track's releases (version codes, status, rollout) and the product list.
Read-only (opens an edit and throws it away). python3 tools/play/status.py"""
import json, os
from googleapiclient.discovery import build
PKG = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'products.json')))['package']
api = build('androidpublisher', 'v3', cache_discovery=False)
edit = api.edits().insert(packageName=PKG, body={}).execute()['id']
try:
    for t in api.edits().tracks().list(packageName=PKG, editId=edit).execute().get('tracks', []):
        for r in t.get('releases', []) or [{}]:
            print(f"track {t['track']:<22} {r.get('status','-'):<12} codes {r.get('versionCodes', [])} name {r.get('name','')} fraction {r.get('userFraction','')}")
    for b in api.edits().bundles().list(packageName=PKG, editId=edit).execute().get('bundles', []):
        print('bundle versionCode', b.get('versionCode'))
finally:
    api.edits().delete(packageName=PKG, editId=edit).execute()
ot = api.monetization().onetimeproducts().list(packageName=PKG).execute().get('oneTimeProducts', [])
print('one-time products:', ', '.join(f"{p['productId']}({p['purchaseOptions'][0].get('state','?')})" for p in ot))
subs = api.monetization().subscriptions().list(packageName=PKG).execute().get('subscriptions', [])
print('subscriptions:', ', '.join(f"{s['productId']}({s['basePlans'][0].get('state','?')})" for s in subs))
