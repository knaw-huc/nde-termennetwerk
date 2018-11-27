package nl.knaw.huc.di.nde;

import java.io.FileInputStream;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.ServerConfiguration;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.message.internal.ReaderWriter;

/**
 * Main class.
 *
 */
public class Main {
    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://localhost:8080/nde/";
    public static final String APP_PATH = "/static";
    public static final String WEB_ROOT = "/static";

    /**
     * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
     * @return Grizzly HTTP server.
     */
    public static HttpServer startServer() throws IOException {
        // create a resource config that scans for JAX-RS resources and providers
        // in nde-registry package
        final ResourceConfig rc = new ResourceConfig().packages("nl.knaw.huc.di.nde").register(JacksonFeature.class);

        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc, false);
        final ServerConfiguration config = server.getServerConfiguration();
        // add handler for serving static content
        config.addHttpHandler(new StaticContentHandler(null), APP_PATH);
        server.start();
        return server;
    }

    /**
     * Main method.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        final HttpServer server = startServer();
        System.out.println(String.format("nde ndE nDE NDE NDe Nde nde ndE nDE NDE NDe Nde nde ndE nDE NDE NDe Nde nde\nHit enter to END it...", BASE_URI));
        System.in.read();
        server.stop();
    }
    
    /**
     * Simple HttpHandler for serving static content included in web root
     * directory of this application.
     * 
     * Source: https://www.javatips.net/api/geosummly-master/src/main/java/it/unito/geosummly/Server.java
     */
    private static class StaticContentHandler extends HttpHandler {
        private static final HashMap<String, String> EXTENSION_TO_MEDIA_TYPE;

        static {
            EXTENSION_TO_MEDIA_TYPE = new HashMap<String, String>();
            EXTENSION_TO_MEDIA_TYPE.put("html", "text/html; charset=utf-8");
            EXTENSION_TO_MEDIA_TYPE.put("js", "application/javascript; charset=utf-8");
            EXTENSION_TO_MEDIA_TYPE.put("map", "application/javascript; charset=utf-8");
            EXTENSION_TO_MEDIA_TYPE.put("css", "text/css; charset=utf-8");
            EXTENSION_TO_MEDIA_TYPE.put("png", "image/png");
            EXTENSION_TO_MEDIA_TYPE.put("ico", "image/png");
            EXTENSION_TO_MEDIA_TYPE.put("json", "text/json; charset=utf-8");
            EXTENSION_TO_MEDIA_TYPE.put("geojson", "text/geojson; charset=utf-8");
            EXTENSION_TO_MEDIA_TYPE.put("pdf", "application/pdf");
            EXTENSION_TO_MEDIA_TYPE.put("gif", "image/gif");
        }

        private final String webRootPath;

        StaticContentHandler(String webRootPath) {
            this.webRootPath = webRootPath;
        }

        @Override
        public  void service(Request request, Response response) 
        throws Exception 
        {
            String uri = request.getRequestURI();
            
            int pos = uri.lastIndexOf('.');
            String extension = uri.substring(pos + 1);
            String mediaType = EXTENSION_TO_MEDIA_TYPE.get(extension);

            if (!uri.equals("/") && ( uri.contains("..") || mediaType == null) ) {
                response.sendError(HttpStatus.NOT_FOUND_404.getStatusCode());
                return;
            }
            
            final String resourcesContextPath = request.getContextPath();
            if (resourcesContextPath != null && !resourcesContextPath.isEmpty()) {
                if (!uri.startsWith(resourcesContextPath)) {
                    response.sendError(HttpStatus.NOT_FOUND_404.getStatusCode());
                    return;
                }

                uri = uri.substring(resourcesContextPath.length());
            }

            uri = uri.equals("/") ? uri.concat("index.html") : uri;
            System.out.println(uri);
            InputStream fileStream;

            try {
                fileStream = webRootPath == null ?
                        Main.class.getResourceAsStream(WEB_ROOT + uri) :
                        new FileInputStream(webRootPath + uri);
            } catch (IOException e) {
                fileStream = null;
            }
            if (fileStream == null) {
                response.sendError(HttpStatus.NOT_FOUND_404.getStatusCode());
            } else {
                response.setStatus(HttpStatus.OK_200);
                response.setContentType(mediaType);
                ReaderWriter.writeTo(fileStream, response.getOutputStream());
            }
        }
    }
}

