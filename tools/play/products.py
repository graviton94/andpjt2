"""Create or update the Play in-app products and subscriptions from products.json (idempotent).
Needs: GOOGLE_APPLICATION_CREDENTIALS (service account with the app's financial/products permission in Play Console)
and an AAB with the BILLING permission already uploaded to a track (Play refuses products before that).
python3 tools/play/products.py [--dry-run]
"""
import json, os, sys
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, 'products.json')))
PKG, DRY = CFG['package'], '--dry-run' in sys.argv
api = build('androidpublisher', 'v3', cache_discovery=False)
LANGS = {'en': 'en-US', 'ko': 'ko-KR'}


def inapp(p):
    body = {'packageName': PKG, 'sku': p['id'], 'status': 'active', 'purchaseType': 'managedUser', 'defaultLanguage': 'en-US',
            'defaultPrice': {'priceMicros': str(p['krw'] * 1_000_000), 'currency': 'KRW'},
            'listings': {LANGS[k]: {'title': p[k][0], 'description': p[k][1]} for k in LANGS}}
    if DRY: return print('inapp', p['id'], p['krw'])
    try:
        api.inappproducts().get(packageName=PKG, sku=p['id']).execute()
        api.inappproducts().update(packageName=PKG, sku=p['id'], body=body, autoConvertMissingPrices=True).execute(); print('updated', p['id'])
    except HttpError as e:
        if e.resp.status != 404: raise
        api.inappproducts().insert(packageName=PKG, body=body, autoConvertMissingPrices=True).execute(); print('created', p['id'])


def sub(p):
    conv = api.monetization().convertRegionPrices(packageName=PKG, body={'price': {'currencyCode': 'KRW', 'units': str(p['krw'])}}).execute()
    regional = [{'regionCode': r, 'newSubscriberAvailability': True, 'price': v['price']} for r, v in conv.get('convertedRegionPrices', {}).items()]
    other = conv.get('convertedOtherRegionsPrice', {})
    plan = {'basePlanId': 'monthly', 'autoRenewingBasePlanType': {'billingPeriodDuration': 'P1M', 'gracePeriodDuration': 'P3D', 'resubscribeState': 'RESUBSCRIBE_STATE_ACTIVE'},
            'regionalConfigs': regional}
    if other: plan['otherRegionsConfig'] = {'usdPrice': other['usdPrice'], 'eurPrice': other['eurPrice'], 'newSubscriberAvailability': True}
    body = {'packageName': PKG, 'productId': p['id'], 'basePlans': [plan],
            'listings': [{'languageCode': LANGS[k], 'title': p[k][0], 'benefits': [p[k][1][:40]], 'description': p[k][1][:80]} for k in LANGS]}
    if DRY: return print('sub', p['id'], p['krw'], len(regional), 'regions')
    subs = api.monetization().subscriptions()
    try:
        subs.get(packageName=PKG, productId=p['id']).execute()
        subs.patch(packageName=PKG, productId=p['id'], body=body, updateMask='listings,basePlans', regionsVersion_version='2022/02').execute(); print('updated', p['id'])
    except HttpError as e:
        if e.resp.status != 404: raise
        subs.create(packageName=PKG, productId=p['id'], body=body, regionsVersion_version='2022/02').execute(); print('created', p['id'])
    try:
        subs.basePlans().activate(packageName=PKG, productId=p['id'], basePlanId='monthly', body={}).execute(); print('  base plan active')
    except HttpError as e:
        print('  base plan activate:', e.resp.status, str(e)[:160])


for p in CFG['inapp']: inapp(p)
for p in CFG['subs']: sub(p)
