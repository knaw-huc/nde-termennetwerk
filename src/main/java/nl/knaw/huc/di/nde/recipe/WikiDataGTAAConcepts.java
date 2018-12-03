package nl.knaw.huc.di.nde.recipe;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import nl.knaw.huc.di.nde.Registry;
import nl.knaw.huc.di.nde.TermDTO;
import nl.mpi.tla.util.Saxon;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.*;

//TODO: Get this to inherit nicely from Wikdata recipe
public class WikiDataGTAAConcepts implements RecipeInterface {
    
    final static public Map<String,String> NAMESPACES = new LinkedHashMap<>();
    
    static {
        NAMESPACES.putAll(Registry.NAMESPACES);
        NAMESPACES.put("wikidata", "https://www.wikidata.org/wiki/");
        NAMESPACES.put("gtaa", "http://data.beeldengeluid.nl/gtaa/");
    };
    
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
            System.err.println("DBG: Lets cook some WikiData!");
            String api = Saxon.xpath2string(config, "nde:api", null, WikiDataGTAAConcepts.NAMESPACES);
            String cs  = Saxon.xpath2string(config, "nde:conceptScheme", null, WikiDataGTAAConcepts.NAMESPACES);
            System.err.println("DBG: Ingredients:");
            System.err.println("DBG: - instance["+Saxon.xpath2string(config, "(nde:label)[1]", null, WikiDataGTAAConcepts.NAMESPACES)+"]");
            System.err.println("DBG: - api["+api+"]");
            System.err.println("DBG: - conceptScheme["+cs+"]");
            System.err.println("DBG: - match["+match+"]");
            // https://www.wikidata.org/w/api.php?action=wbsearchentities&search=andre%20van%20duin&format=json&language=en&type=item&continue=0&props=www.wikidata.org/wiki/Property:P1741
            //TODO: proper URL escaping
            URL url = new URL(api + "/w/api.php?action=wbsearchentities&search="+  match.replace(" ","%20") + "&language=en&format=json&type=item&continue=0");//api+"/find-concepts?q=prefLabel:"+match+"&conceptScheme="+cs+"&fl=uri,prefLabel,altLabel");
            System.err.println("DBG: = url["+url+"]");
            JSONObject termsObject = new JSONObject(jsonGetRequest(url));
            System.err.println("DBG: " + jsonGetRequest(url));

			JSONArray termsArray = termsObject.getJSONArray("search");
			for (int i = 0; i < termsArray.length(); i++)
			{
				JSONObject termObject = termsArray.getJSONObject(i);
				TermDTO term = new TermDTO();
				
				
				//TODO: try to get all the labels, not just the one returned by the query
				term.prefLabel = new ArrayList<>();
				term.prefLabel.add(termObject.getString("label"));
				
				term.altLabel = new ArrayList<>();
				term.altLabel.add(termObject.getString("label"));				

				
				//check if is GTAA and get the other labels via wbgetclaims
				//https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&props=value&entity=Q523644
	            URL claimsUrl = new URL(api + "/w/api.php?action=wbgetclaims&entity="+  termObject.getString("id") + "&format=json&props=values");//api+"/find-concepts?q=prefLabel:"+match+"&conceptScheme="+cs+"&fl=uri,prefLabel,altLabel");
	            System.err.println("DBG: Claims URL " + claimsUrl);
	            JSONObject claimsUrlObject = new JSONObject(jsonGetRequest(claimsUrl));
	            
	            JSONObject claimsObject = claimsUrlObject.getJSONObject("claims");
	            if(claimsObject.has("P1741")) // if item is linked to GTAA
	            {
	            	JSONArray gtaaArray = claimsObject.getJSONArray("P1741");
	            	//assume only one match, this should be otherwise we have a data issue in the GTAA
	            	JSONObject gtaaInfo = gtaaArray.getJSONObject(0);
	            	JSONObject gtaaElement = gtaaInfo.getJSONObject("mainsnak");
	            	JSONObject gtaaValue = gtaaElement.getJSONObject("datavalue");
					term.uri = new URI(WikiDataGTAAConcepts.NAMESPACES.get("gtaa") + gtaaValue.getString("value"));
					System.err.println("DBG: GTAA URI " + term.uri);
            		terms.add(term);

	            	//TODO: get the labels - can this via claims or are they not there?
	            }
				
			}

        } catch (SaxonApiException | MalformedURLException  | URISyntaxException ex) {
            Logger.getLogger(WikiDataGTAAConcepts.class.getName()).log(Level.SEVERE, null, ex);
        }
        return terms;
    }
    
}
