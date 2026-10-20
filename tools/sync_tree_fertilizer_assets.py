"""Pinned tree fertilizer only, preserving native optional integration conditions."""
import json
def sync_tree_fertilizer_assets(g):
 find,write_json,copy_model=(g[k] for k in ('find','write_json','copy_model'));copy_model('create:item/tree_fertilizer')
 write_json('assets/create/items/tree_fertilizer.json',{'model':{'type':'minecraft:model','model':'create:item/tree_fertilizer'}})
 recipes=('crafting/appliances/tree_fertilizer','crafting/tree_fertilizer')
 def conditions(data):
  old=data.pop('neoforge:conditions',[])
  if old:
   assert old==[{'type':'neoforge:mod_loaded','modid':'upgrade_aquatic'}]
   data['fabric:load_conditions']=[{'condition':'fabric:all_mods_loaded','values':['upgrade_aquatic']}]
  return data
 for recipe in recipes:
  rel=f'data/create/recipe/{recipe}.json';write_json(rel,g['modern_ingredients'](conditions(json.loads(find(rel).read_text()))))
  rel=f'data/create/advancement/recipes/misc/{recipe}.json';data=conditions(json.loads(find(rel).read_text()))
  # Source optional advancement accidentally names crafting///tree_fertilizer; target its actual source file.
  for criterion in data.get('criteria',{}).values():
   if criterion.get('trigger')=='minecraft:recipe_unlocked':criterion['conditions']['recipe']='create:'+recipe
  data['rewards']['recipes']=['create:'+recipe];write_json(rel,g['modern_advancement'](data))
 rel='data/create/tags/item/upgrade_aquatic/coral.json';write_json(rel,json.loads(find(rel).read_text()))
 for locale in ('en_us','ru_ru','en_ud'):
  rel=f'assets/create/lang/{locale}.json';data=json.loads(find(rel).read_text());write_json(rel,{k:v for k,v in data.items() if k=='item.create.tree_fertilizer'})
 return {'owner':'tree_fertilizer','sourceRecipeIds':recipes,'optionalMod':'upgrade_aquatic','optionalRecipeAndAdvancementConditionsPreserved':True,'optionalSourceRecipeReferenceCanonicalized':True,'nativeRuntimeVerified':False,'blueprintIncluded':False}
