import socketserver

class MasterServer(socketserver.StreamRequestHandler):

	def handle(self):
		data = self.rfile.readline().strip()
		print(data)

if __name__ == "__main__":
	server = socketserver.TCPServer(
		("localhost", 43760), MasterServer)

	server.serve_forever()
