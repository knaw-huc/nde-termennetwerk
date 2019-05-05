package nl.knaw.huc.di.nde.recipe;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
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

    // function translating the results into GraphQL Term vars
    private static TermDTO processItem(JSONObject itemObject, TermDTO term, String base) {

        String name =""; String uristr = ""; String id = "";
        URI uri = null;
        try {
            if (itemObject.has("name")) { name = itemObject.getString("name"); }
            if (itemObject.has("uri")) { uristr = itemObject.getString("uri"); }
            if (itemObject.has("id")) { uristr = itemObject.getString("id"); }
            if ( uristr.toLowerCase().contains("http") ) {
                uri = new URI(uristr);
            }
            else {
                // build a uri based on the erfgeo url in 'base'
                uri = new URI(base + uristr); 
            }
            // first start with uri and prefLabel
            if (term.uri == null ) {
                term.uri=uri;
                term.prefLabel.add(name);
            }
            // all the others are added as related terms
            else {
                term.related.add(name + " | " + uri);
            }
        }
        catch (URISyntaxException ex) {
            Logger.getLogger(ErfGeo.class.getName()).log(Level.SEVERE, null, ex);
        }
        return term;
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
            //
            // original erfgeo api returns a large number of results for example:
            // https://api.histograph.io/search?q=hoorn&type=hg:Street&geometry=false
            //
            // instead we use the hicsuntleonis proxy
            // https://www.hicsuntleones.nl/erfgeoproxy/search?q=hoorn&type=hg:Place&geometry=false
            //
            URL url = new URL(api + "search?q="+  match + "&type=" + type + "&geometry=false");
            System.err.println("DBG: = url["+url+"]");
            JSONObject returnObject = new JSONObject(jsonGetRequest(url));
            System.err.println("DBG: " + jsonGetRequest(url));
            TermDTO term = null;
			JSONArray resultsArray = returnObject.getJSONArray("results");
			for (int i = 0; i < resultsArray.length(); i++)
			{
                System.err.println("DBG: - result number: "+ i);
                JSONObject resultObject = resultsArray.getJSONObject(i);
                term = new TermDTO();
                // todo: reduce the next part to something with a functional call
                // todo: implement broader scope then only hg:Place for this part
                // see if one of the results is a geonames reference
                // if so use this would be a good uri candidate
                if ( type == "hg:Place" & resultObject.has("geonames") ) {
                    JSONObject itemObject = resultObject.getJSONObject("geonames");
                    term = processItem(itemObject, term, base);
                    resultObject.remove("geonames");
                }
                // see if there is a getty uri 
                // it will be used as uri when there was no geonames uri
                // otherwise it will be add as one the related results
                if ( type == "hg:Place" & resultObject.has("tgn") ) {
                    JSONObject itemObject = resultObject.getJSONObject("tgn");
                    term = processItem(itemObject, term, base);
                    resultObject.remove("tgn");
                }
                // same for the bag uri when no geonames or tgn is available
                if ( type == "hg:Place" & resultObject.has("bag") ) {
                    JSONObject itemObject = resultObject.getJSONObject("bag");
                    term = processItem(itemObject, term, base);
                    resultObject.remove("bag");
                }
                // add the labels a alternate names in the result
                if ( resultObject.has("known-names") ) {
                    term.altLabel.add(resultObject.getString("known-names"));
                    resultObject.remove("know-names");
                }
                Iterator<String> keys = resultObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    System.err.println("DBG: - working on key: "+ key);
                    // record the type field
                    if (key == "type" ) {
                       term.scopeNote.add(resultObject.getString("type"));
                       
                       // no further processing necessary 
                       continue;
                    }
                    // the other results are the 'places in time' (pits)
                    // structured as JSON objects or arrays 
                    if (resultObject.get(key) instanceof JSONObject) {
                        JSONObject itemObject = resultObject.getJSONObject(key);
                        term = processItem(itemObject, term, base);
                    }
                    if (resultObject.get(key) instanceof JSONArray) {
                        JSONArray itemArray = resultObject.getJSONArray(key);
                        for (int j = 0; j < itemArray.length(); j++) {
                            JSONObject itemObject = itemArray.getJSONObject(j);
                            term = processItem(itemObject, term, base);
                        }
                    }           
                }
                // store the grouped resultset
                terms.add(term);
            }
             
        } catch (IOException | SaxonApiException ex) {
            Logger.getLogger(ErfGeo.class.getName()).log(Level.SEVERE, null, ex);
        }
        return terms;
    }
    
}
