# grabbed from webbukkit/DynmapCore
# used 

import re, urllib.request, os

colors = list()
hasdata = set()

with urllib.request.urlopen('https://raw.github.com/webbukkit/DynmapCore/master/colorschemes/default.txt') as q:
	for m in re.finditer("^(\d+)(:(\d+))?\s+(\d+)\s+(\d+)\s+(\d+)", q.read().decode('utf8'), re.MULTILINE):
		m, d, r, g, b = m.group(1, 3, 4, 5, 6)
		if d is None: d = -1
		else: hasdata.add(m)
		
		colors.append((m, d, r, g, b))

def isvalid(hasdata, m, d, r, g, b):
	return d != -1 or m not in hasdata

# get rid of the non-data versions of blocks that have data versions
colors = [row for row in colors if isvalid(hasdata, *row)]

with open(os.path.join(os.path.dirname(__file__), 'src', 'main', 'resources', 'colors.csv'), 'w') as f:
	for m, d, r, g, b in colors:
		print(m, d, r, g, b, file=f)
