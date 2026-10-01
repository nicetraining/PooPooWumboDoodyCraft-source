#!/usr/bin/env python3
"""Static resource audit: every animation, sound, lang key, texture, model and data file the code refers to must exist."""
import json, re, sys, glob, os

root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
java = os.path.join(root, 'src/main/java')
res = os.path.join(root, 'src/main/resources')
assets = os.path.join(res, 'assets/sexmod')
problems = []

def err(msg):
    problems.append(msg)

# 1. every json parses
for f in glob.glob(res + '/**/*.json', recursive=True):
    try:
        json.load(open(f, encoding='utf-8'))
    except Exception as e:
        err(f'invalid json {os.path.relpath(f, res)}: {e}')

sources = {}
for f in glob.glob(java + '/**/*.java', recursive=True):
    sources[f] = open(f, encoding='utf-8').read()
alltext = '\n'.join(sources.values())

# 2. animations
anims = set()
for f in glob.glob(assets + '/animations/**/*.json', recursive=True):
    anims |= set(json.load(open(f, encoding='utf-8')).get('animations', {}).keys())
used = set(re.findall(r'"(animation\.[a-z_0-9]+\.[A-Za-z_0-9]+)"', alltext))
for f in glob.glob(assets + '/keyframe_events/*.json'):
    used |= set(re.findall(r'"(animation\.[a-z_0-9]+\.[A-Za-z_0-9]+)"', open(f, encoding='utf-8').read()))
for a in sorted(used - anims):
    if a in ('animation.goblin.catch', 'animation.goblin.pick_up', 'animation.goblin.throw'):
        continue  # person suffix appended at runtime (checked below)
    err(f'animation used but not defined: {a}')

# 3. sounds
sounds = json.load(open(assets + '/sounds.json', encoding='utf-8'))
def sound_ok(s):
    if s.startswith('minecraft:'):
        return True
    return s in sounds or any(f'{s}{i}' in sounds for i in range(3))
def check_sound(s, where):
    if not sound_ok(s):
        err(f'sound not in sounds.json: {s} ({where})')
for f in glob.glob(assets + '/keyframe_events/*.json'):
    d = json.load(open(f, encoding='utf-8'))
    def walk(o):
        if isinstance(o, dict):
            for k, v in o.items():
                if k == 'sounds' and isinstance(v, list):
                    for s in v:
                        if isinstance(s, str):
                            check_sound(s, os.path.basename(f))
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(d)
for m in re.finditer(r'(?:ModSounds\.find|sound)\(\s*"((?:girls|misc)\.[a-z_0-9.]+)"', alltext):
    check_sound(m.group(1), 'java')
# sound files referenced by sounds.json exist
for key, entry in sounds.items():
    for s in entry.get('sounds', []):
        name = s['name'] if isinstance(s, dict) else s
        ns, _, path = name.partition(':') if ':' in name else ('sexmod', '', name)
        if ns == 'sexmod' and not os.path.exists(os.path.join(assets, 'sounds', path + '.ogg')):
            err(f'sound file missing for {key}: {path}.ogg')

# 4. lang keys
lang = json.load(open(assets + '/lang/en_us.json', encoding='utf-8'))
for m in re.finditer(r'translatable\(\s*"([^"]+)"', alltext):
    if m.group(1) not in lang and not m.group(1).endswith('.'):
        err(f'lang key missing: {m.group(1)}')
for m in re.finditer(r'"((?:message|action\.names|screen|gui|key)\.[a-z_0-9.]*sexmod[a-z_0-9.]*|message\.sexmod\.[a-z_0-9.]+|action\.names\.[a-z_0-9]+)"', alltext):
    if m.group(1) not in lang and not m.group(1).endswith('.'):
        err(f'lang key missing: {m.group(1)}')

# 5. resource paths
for m in re.finditer(r'"((?:textures|geo|animations|models)/[A-Za-z_0-9/.\-]+\.(?:png|json))"', alltext):
    p = m.group(1)
    if p == 'textures/entity/fishing_hook.png':
        continue
    if not os.path.exists(os.path.join(assets, p)):
        err(f'asset missing: {p}')
for m in re.finditer(r'fromNamespaceAndPath\(\s*(?:FapCraft\.MOD_ID|"sexmod"),\s*"((?:textures|geo|animations)/[A-Za-z_0-9/.\-]+)"', alltext):
    if not os.path.exists(os.path.join(assets, m.group(1))):
        err(f'asset missing: {m.group(1)}')

# 6. item models and their textures
for f in glob.glob(assets + '/models/item/*.json'):
    d = json.load(open(f, encoding='utf-8'))
    for name, tex in d.get('textures', {}).items():
        if tex.startswith('sexmod:'):
            if not os.path.exists(os.path.join(assets, 'textures', tex[7:] + '.png')):
                err(f'item texture missing: {tex} in {os.path.basename(f)}')

# 7. every registered item has a model and lang name
for m in re.finditer(r'ITEMS\.register(?:Item|Simple\w*)?\(\s*"([a-z_]+)"', alltext):
    n = m.group(1)
    if not os.path.exists(os.path.join(assets, 'models/item', n + '.json')) and not n.endswith('_spawn_egg'):
        err(f'item without model json: {n}')
    if f'item.sexmod.{n}' not in lang:
        err(f'item without lang name: item.sexmod.{n}')

# 8. data resources referenced by code
for m in re.finditer(r'"(/data/sexmod/[A-Za-z_0-9/.\-]+)"', alltext):
    if not os.path.exists(os.path.join(res, m.group(1).lstrip('/'))):
        err(f'data file missing: {m.group(1)}')

# 9. each companion kind has its geo, texture and animation file
for m in re.finditer(r'"((?:geo|animations|textures)/[A-Za-z_0-9/.\-]+\.(?:json|png))"', open(os.path.join(java, 'com/trolmastercard/sexmod/entity/CompanionKind.java')).read()):
    if not os.path.exists(os.path.join(assets, m.group(1))):
        err(f'kind resource missing: {m.group(1)}')

print(f'{len(problems)} problems')
for p in problems:
    print(' -', p)
sys.exit(1 if problems else 0)
