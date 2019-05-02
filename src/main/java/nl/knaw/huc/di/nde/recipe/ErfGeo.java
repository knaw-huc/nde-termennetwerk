package nl.knaw.huc.di.nde.recipe;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import nl.knaw.huc.di.nde.Registry;
import nl.knaw.huc.di.nde.TermDTO;
import nl.mpi.tla.util.Saxon;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;
import org.json.*;


public class ErfGeo implements RecipeInterface {
    
    private static String streamToString(InputStream inputStream) {
        String text = new Scanner(inputStream, "UTF-8").useDelimiter("\\Z").next();
        return text;
      }

      public static String jsonGetRequest(URL url) {
        String json = null;
        try {
          HttpURLConnection connection = (HttpURLConnection) url.openConnection();
          connection.setDoOutput(true);
          connection.setInstanceFollowRedirects(false);
          connection.setRequestMethod("GET");
          connection.setRequestProperty("Content-Type", "application/json");
          connection.setRequestProperty("charset", "utf-8");
          connection.connect();
          InputStream inStream = connection.getInputStream();
          json = streamToString(inStream); // input stream to string
        } catch (IOException ex) {
          ex.printStackTrace();
        }
        return json;
      }

    @Override
    public List<TermDTO> fetchMatchingTerms(XdmItem config, String match) {
        List<TermDTO> terms = new ArrayList<>();
        try {
            System.err.println("DBG: Lets cook some ErfGeo!");
            String api = Saxon.xpath2string(config, "nde:api", null, Registry.NAMESPACES);
            
            // see if api supports the use of '*'; should be boolean instead of string
            String wildcard = Saxon.xpath2string(config, "nde:wildcard",null, Registry.NAMESPACES);
            String base = Saxon.xpath2string(config, "nde:base",null, Registry.NAMESPACES);
            String type = Saxon.xpath2string(config, "nde:type",null, Registry.NAMESPACES);
            // remove '*' if wildcards are not supported
            if ( wildcard.equals("no") ) {
                match = match.replaceAll("\\*","");
            }

            // encode the match string 
            match = URLEncoder.encode(match, "UTF-8");

            System.err.println("DBG: Ingredients:");
            System.err.println("DBG: - instance["+Saxon.xpath2string(config, "(nde:label)[1]", null, Registry.NAMESPACES)+"]");
            System.err.println("DBG: - api["+api+"]");
            System.err.println("DBG: - match["+match+"]");
            // https://api.histograph.io/search?q=${match}&type=hg:Street&geometry=false
            URL url = new URL(api + "search?q="+  match + "&type=" + type + "&geometry=false");
            System.err.println("DBG: = url["+url+"]");
            JSONObject returnObject = new JSONObject(jsonGetRequest(url));
            System.err.println("DBG: " + jsonGetRequest(url));

			JSONArray featuresArray = returnObject.getJSONArray("features");
			for (int i = 0; i < featuresArray.length(); i++)
			{
                JSONObject featureObject = featuresArray.getJSONObject(i);
                JSONObject propertyObject = featureObject.getJSONObject("properties");
                JSONArray pitsArray = propertyObject.getJSONArray("pits");
                for (int j=0; j< pitsArray.length(); j++) {
                    TermDTO term = new TermDTO();
                    JSONObject pitObject = pitsArray.getJSONObject(j);
                    String id = pitObject.getString("@id");
                    // check if the id-field contains a uri
                    if ( id.toLowerCase().contains("http") ) {
                       term.uri = new URI(id);
                    }
                    else {
                       term.uri = new URI(base+id); 
                    }
                    if (pitObject.has("name")) {
                        term.prefLabel.add(pitObject.getString("name"));
                    }
                    if (pitObject.has("dataset")) {
                        term.scopeNote.add("dataset: " + pitObject.getString("dataset"));
                    }
                    if (pitObject.has("type")) {
                        term.scopeNote.add("type: " + pitObject.getString("type"));
                    }                   
                    if (pitObject.has("validSince")){
                        JSONArray validSince = pitObject.getJSONArray("validSince");
                        if ( validSince.length() > 0 ) {
                            if (validSince.length() == 2 ) {
                                term.definition.add("geldig vanaf " + validSince.getString(0) + "/" + validSince.getString(1));
                            }
                            else {
                                term.definition.add("geldig vanaf " + validSince.getString(0)) ;
                            }
                            
                        }
                    }
                    if (pitObject.has("validUntil")) {
                        JSONArray validUntil = pitObject.getJSONArray("validUntil");
                        if ( validUntil.length() > 0 ) {
                            if (validUntil.length() == 2 ) {
                                term.definition.add("geldig tot " + validUntil.getString(0) + "/" + validUntil.getString(0));
                            }
                            else {
                                term.definition.add("geldig tot " + validUntil.getString(0)) ;
                            }
                        }
                    }                    
                    terms.add(term);
                }
            }
             
        } catch (IOException | SaxonApiException | URISyntaxException ex) {
            Logger.getLogger(ErfGeo.class.getName()).log(Level.SEVERE, null, ex);
        }
        return terms;
    }
    
}
