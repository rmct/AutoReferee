import pystache
import sys, csv, json
import os

prog, cmdfile = sys.argv
print("Updating command page for AutoReferee from " + cmdfile)

data = {  }

ROLES = ['Any', 'Players', 'Spectator', 'Streamer', 'Referee' ]

cmdlist = []
with open(cmdfile, 'r') as inp:
	for cmd, opt, opthelp, perms, roleord, console, desc, usage in csv.reader(inp, delimiter='|'):
		rolename = ROLES[int(roleord)]

		optargs = False
		if len(opthelp):
			opthelp = opthelp.split('#')
			args = zip(opthelp[0::2], opthelp[1::2])
			
			optargs = dict()
			
			optargs['args'] = list()
			for arg,argdesc in args:
				optargs['args'].append({ 'arg': arg, 'argdesc': argdesc })
		
		cmdlist.append({
			'id': 'command-' + cmd.replace(' ', '-'),
			'cmd': '/' + cmd,
			'opthelp': optargs,
			'perm': False if not len(perms) else { 'node': perms },
			'role': rolename,
			'console': bool(int(console)),
			'desc': desc,
			'usage': usage,
		})

cmdlist.sort(key=lambda x:x['cmd'])
data['commands'] = cmdlist

with open(os.path.join('templates', 'commands.html'), 'r') as inp:
	template = inp.read().encode('utf8')

	with open('commands.html', 'w') as oup:
		oup.write(pystache.render(template, data))

