import SimpleHTTPServer
import SocketServer
import logging
import cgi

PORT = 8000

class ServerHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):

    def do_GET(self):
        logging.error(self.headers)
        SimpleHTTPServer.SimpleHTTPRequestHandler.do_GET(self)

    def do_POST(self):
        logging.error(self.headers)
        print(self.rfile.read(int(self.headers['Content-Length'])).decode("UTF-8"))
        SimpleHTTPServer.SimpleHTTPRequestHandler.do_GET(self)

if __name__ == '__main__':
    try:
        Handler = ServerHandler
        httpd = SocketServer.TCPServer(("", PORT), Handler)
        print "serving at port", PORT
        httpd.serve_forever()
    except KeyboardInterrupt:
        print "Caught ^C shutting down"
        httpd.socket.close()
    except Exception as e:
        print e
        httpd.socket.close()
