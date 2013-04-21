import urllib.request
import re

# these colors are borrowed from webbukkit/DynmapCore
COLOR_FILE = 'http://raw.github.com/webbukkit/DynmapCore/master/colorschemes/default.txt'

def check(line):
	parts = line.split()
	return len(parts) and re.match('\d+(:\d+)?', parts[0])

with urllib.request.urlopen(COLOR_FILE) as u:
	lines = [line.split() for line in u.read().decode('utf8').splitlines() if check(line)]

materials = dict()
for line in lines:
	info, r, g, b, *rest = line
	parts = info.split(':')

	mat = int(parts[0])
	if mat not in materials:
		materials[mat] = dict()

	data = int(parts[1]) if len(parts) > 1 else -1
	materials[mat][data] = (r, g, b)

for mat in sorted(materials.keys()):
	if len(materials[mat]) > 1:
		del materials[mat][-1]
	for data in sorted(materials[mat].keys()):
		r, g, b = materials[mat][data]
		print("%d %d %s %s %s" % (mat, data, r, g, b))
