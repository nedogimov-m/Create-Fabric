"""Pinned Hatch/Sword resources only. Fertilizer and Blueprint remain separate active source work."""
import json
OWNERS=('item_hatch','cardboard_sword')
def sync_hatch_sword_assets(g):
 find,write,write_json,copy_model,walk_models=(g[k] for k in ('find','write','write_json','copy_model','walk_models'))
 rel='assets/create/blockstates/item_hatch.json';state=json.loads(find(rel).read_text());write_json(rel,state);walk_models(state)
 rel='data/create/loot_table/blocks/item_hatch.json';write_json(rel,g['modern_loot'](json.loads(find(rel).read_text())))
 for name in OWNERS:copy_model('create:item/'+name)
 copy_model('create:item/cardboard_sword/item_in_hand')
 write_json('assets/create/items/item_hatch.json',{'model':{'type':'minecraft:model','model':'create:item/item_hatch'}})
 # Original custom renderer applies the ORIGINAL item model's transforms; PartialItemModelRenderer
 # renders HELD quads without applying HELD.display. Original item inherits generated/identity.
 held=json.loads(find('assets/create/models/item/cardboard_sword/item_in_hand.json').read_text());held['display']={context:{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]} for context in ('thirdperson_righthand','thirdperson_lefthand','firstperson_righthand','firstperson_lefthand','head','gui','ground','fixed')};write_json('assets/create/models/item/cardboard_sword/native_held.json',held)
 write_json('assets/create/items/cardboard_sword.json',{'model':{'type':'minecraft:select','property':'minecraft:display_context','cases':[{'when':'gui','model':{'type':'minecraft:model','model':'create:item/cardboard_sword'}}],'fallback':{'type':'minecraft:model','model':'create:item/cardboard_sword/native_held'}}})
 owned={'create:'+x for x in OWNERS};root=g['ROOTS'][1]
 for ns in ('minecraft','create','c'):
  for kind in ('block','item'):
   for path in sorted((root/f'data/{ns}/tags/{kind}').rglob('*.json')):
    data=json.loads(path.read_text());values=[x for x in data.get('values',[]) if (x if isinstance(x,str) else x.get('id')) in owned]
    if values:write_json(path.relative_to(root).as_posix(),{'replace':False,'values':values})
 write_json('data/create/tags/item/cardboard_sword_repair.json',{'replace':False,'values':['create:cardboard']})
 recipes=[]
 for path in sorted((root/'data/create/recipe').rglob('*.json')):
  data=json.loads(path.read_text());result=data.get('result',{});identifier=result if isinstance(result,str) else result.get('id')
  if identifier not in owned:continue
  rel=path.relative_to(root).as_posix();write_json(rel,g['modern_ingredients'](data));recipes.append(rel.removeprefix('data/create/recipe/').removesuffix('.json'))
 for path in sorted((root/'data/create/advancement/recipes').rglob('*.json')):
  data=json.loads(path.read_text())
  if any(x.removeprefix('create:') in recipes for x in data.get('rewards',{}).get('recipes',[])):write_json(path.relative_to(root).as_posix(),g['modern_advancement'](data))
 sounds=json.loads(find('assets/create/sounds.json').read_text())
 for name in ('item_hatch','item_hatch_compounded_1','cardboard_bonk'):
  entry=sounds[name];write_json('assets/create/sounds.json',{name:entry})
  for sound in entry['sounds']:
   identifier=sound if isinstance(sound,str) else sound['name'];ns,path=identifier.split(':',1) if ':' in identifier else ('minecraft',identifier)
   if ns=='create':rel='assets/create/sounds/'+path+'.ogg';write(rel,find(rel).read_bytes())
 for locale in ('en_us','ru_ru','en_ud'):
  rel=f'assets/create/lang/{locale}.json';data=json.loads(find(rel).read_text());selected={k:v for k,v in data.items() if k.startswith(('create.item_hatch.','create.subtitle.item_hatch','create.subtitle.cardboard_bonk')) or k in ('block.create.item_hatch','item.create.cardboard_sword')};write_json(rel,selected)
 # Same actual native cooking-time provider schema used by the existing source1000-tick cardboard fuel.
 fuel=json.loads((g['OUTPUT']/'data/create/context_int_provider/cooking/time_cardboard.json').read_text());write_json('data/create/context_int_provider/cooking/time_cardboard_sword.json',fuel)
 return {'owners':OWNERS,'sourceRecipeIds':recipes,'cardboardSwordFuelTicks':1000,'heldModelTransformContract':'original item model identity; HELD.display intentionally unused by pinned partial renderer','fertilizerIncluded':False,'blueprintIncluded':False,'runtimeVerified':False}
