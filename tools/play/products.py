"""Create or update the Play one-time products and subscriptions from products.json (idempotent).
Uses the new monetization API (monetization.onetimeproducts; the legacy inappproducts API is closed for new apps).
Needs GOOGLE_APPLICATION_CREDENTIALS (service account with the app's products permission in Play Console) and an AAB with
the billing permission already uploaded. Play product listings have no image: the game draws its own product art.
python3 tools/play/products.py [--dry-run]
"""
import json, os, sys
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

HERE = os.path.dirname(os.path.abspath(__file__))
CFG = json.load(open(os.path.join(HERE, 'products.json')))
PKG, DRY = CFG['package'], '--dry-run' in sys.argv
api = build('androidpublisher', 'v3', cache_discovery=False)
mon = api.monetization()
LANGS = {'en': 'en-US', 'ko': 'ko-KR'}
RV = '2022/02'  # replaced by the version convertRegionPrices answers with (currencies change, e.g. BG → EUR)
failed = []


def regions(krw):
    global RV
    conv = mon.convertRegionPrices(packageName=PKG, body={'price': {'currencyCode': 'KRW', 'units': str(krw)}}).execute()
    RV = (conv.get('regionVersion') or {}).get('version') or RV
    return conv.get('convertedRegionPrices', {}), conv.get('convertedOtherRegionsPrice', {})


def onetime(p):
    if DRY: return print('one-time', p['id'], p['krw'])
    reg, other = regions(p['krw'])
    option = {'purchaseOptionId': 'buy', 'buyOption': {'legacyCompatible': True, 'multiQuantityEnabled': False},
              'regionalPricingAndAvailabilityConfigs': [{'regionCode': r, 'price': v['price'], 'availability': 'AVAILABLE'} for r, v in reg.items()]}
    if other: option['newRegionsConfig'] = {'usdPrice': other['usdPrice'], 'eurPrice': other['eurPrice'], 'availability': 'AVAILABLE'}
    body = {'packageName': PKG, 'productId': p['id'], 'listings': [{'languageCode': LANGS[k], 'title': p[k][0], 'description': p[k][1]} for k in LANGS],
            'purchaseOptions': [option]}
    mon.onetimeproducts().patch(packageName=PKG, productId=p['id'], body=body, allowMissing=True, updateMask='listings,purchaseOptions', regionsVersion_version=RV).execute()
    mon.onetimeproducts().purchaseOptions().batchUpdateStates(packageName=PKG, productId=p['id'], body={'requests': [
        {'activatePurchaseOptionRequest': {'packageName': PKG, 'productId': p['id'], 'purchaseOptionId': 'buy'}}]}).execute()
    print('ok', p['id'], len(reg), 'regions, active, regions version', RV)


def sub(p):
    if DRY: return print('sub', p['id'], p['krw'])
    reg, other = regions(p['krw'])
    plan = {'basePlanId': 'monthly', 'autoRenewingBasePlanType': {'billingPeriodDuration': 'P1M', 'gracePeriodDuration': 'P3D', 'resubscribeState': 'RESUBSCRIBE_STATE_ACTIVE'},
            'regionalConfigs': [{'regionCode': r, 'newSubscriberAvailability': True, 'price': v['price']} for r, v in reg.items()]}
    if other: plan['otherRegionsConfig'] = {'usdPrice': other['usdPrice'], 'eurPrice': other['eurPrice'], 'newSubscriberAvailability': True}
    body = {'packageName': PKG, 'productId': p['id'], 'basePlans': [plan],
            'listings': [{'languageCode': LANGS[k], 'title': p[k][0], 'benefits': [b[:40] for b in p[k][1].split(', ')[:4]], 'description': p[k][1][:80]} for k in LANGS]}
    subs = mon.subscriptions()
    try:
        subs.get(packageName=PKG, productId=p['id']).execute()
        subs.patch(packageName=PKG, productId=p['id'], body=body, updateMask='listings,basePlans', regionsVersion_version=RV).execute()
    except HttpError as e:
        if e.resp.status != 404: raise
        subs.create(packageName=PKG, productId=p['id'], body=body, regionsVersion_version=RV).execute()
    try:
        subs.basePlans().activate(packageName=PKG, productId=p['id'], basePlanId='monthly', body={}).execute()
    except HttpError as e:
        if 'already' not in str(e).lower(): raise
    print('ok', p['id'], len(reg), 'regions, base plan active')


for kind, fn in (('inapp', onetime), ('subs', sub)):
    for p in CFG[kind]:
        try: fn(p)
        except HttpError as e:
            failed.append(p['id']); print('FAILED', p['id'], e.resp.status, str(e)[:400])
if failed: sys.exit('failed: ' + ', '.join(failed))
