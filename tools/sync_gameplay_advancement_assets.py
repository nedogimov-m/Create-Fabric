"""Exact pinned gameplay criteria, existing native ancestors, and source translations."""
import json

IDS=("anvil_plough","belt_funnel_kiss","chained_drain","chocolate_bucket","crafter_lazy_000","cross_streams","fist_bump","glass_pipe","hand_crank_000","honey_drain","hose_pulley","lava_wheel_00000","long_travel","mechanical_pump_0","package_chute_throw","pipe_organ","self_deploying","train_roadkill","water_supply")

def sync(g):
    pending=list(IDS);seen=set();keys=set()
    while pending:
        name=pending.pop()
        if name in seen:continue
        seen.add(name);relative=f"data/create/advancement/{name}.json";source=g['find'](relative);data=json.loads(source.read_text())
        if name in IDS or not(g['OUTPUT']/relative).is_file():g['write'](relative,source.read_bytes())
        if name in IDS:
            for component in ('title','description'):
                value=data.get('display',{}).get(component,{})
                if isinstance(value,dict)and 'translate'in value:keys.add(value['translate'])
        parent=data.get('parent','')
        if parent.startswith('create:'):pending.append(parent.removeprefix('create:'))
    translations={}
    for source_root in g['ROOTS']:
        for source in sorted((source_root/'assets/create/lang').glob('*.json')):
            selected={k:v for k,v in json.loads(source.read_text()).items()if k in keys}
            if selected:translations.setdefault(source.name,{}).update(selected)
    for filename,selected in translations.items():
        relative='assets/create/lang/'+filename;destination=g['OUTPUT']/relative
        merged=json.loads(destination.read_text())if destination.is_file()else{}
        merged.update(selected);g['write'](relative,(json.dumps(merged,ensure_ascii=False,indent=2)+'\n').encode())
    return {'sourceAdvancements':list(IDS),'ancestors':sorted(seen),'locales':len(translations),'sourceTranslationKeys':sum(map(len,translations.values())),'runtime':'UNRUN'}
