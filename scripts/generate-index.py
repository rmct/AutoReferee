import pystache
import urllib.request, json
import xml.dom.minidom
import os

pom = None
with urllib.request.urlopen('http://raw.github.com/rmct/AutoReferee/master/pom.xml') as req:
    pom = xml.dom.minidom.parse(req)

def getTag(dom, tagname):
    return pom.getElementsByTagName(tagname).item(0).firstChild.nodeValue

data = {
    'autoref-version': '??',
    'autoref-link': 'http://dev.bukkit.org/server-mods/autoreferee',
    'cb-version': 'CB ' + getTag(pom, 'bukkit.version') + '+'
}

with urllib.request.urlopen('http://api.bukget.org/3/plugins/bukkit/autoreferee/release') as req:
    x = json.loads(req.read().decode('utf8'))
    dobj = x['versions'][0]

data['autoref-link'] = dobj['download']
data['cb-version'] = dobj['game_versions'][0] + '+'
data['autoref-version'] = dobj['version']

print("Updating index for AutoReferee {version} for {game_versions[0]}".format(**dobj))

with open(os.path.join('templates', 'index.html'), 'r') as inp:
    template = inp.read().encode('utf8')

    with open('index.html', 'w') as oup:
        oup.write(pystache.render(template, data))

