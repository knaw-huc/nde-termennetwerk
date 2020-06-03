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
import nl.knaw.huc.di.nde.RefDTO;
import nl.mpi.tla.util.Saxon;

public class OpenSKOS implements RecipeInterface {
    
    final static public Map<String,String> NAMESPACES = new LinkedHashMap<>();
    
    static {
        NAMESPACES.putAll(Registry.NAMESPACES);
        NAMESPACES.put("openskos", "http://openskos.org/xmlns#");
    };

    @Override
    public List<TermDTO> fetchMatchingTerms(XdmItem config, String match) {
        List<TermDTO> terms = new ArrayList<>();
        try {
            System.err.println("DBG: Lets cook some OpenSKOS!");
            String api = Saxon.xpath2string(config, "nde:api", null, OpenSKOS.NAMESPACES);
            String cs  = Saxon.xpath2string(config, "nde:conceptScheme", null, OpenSKOS.NAMESPACES);

            System.err.println("DBG: Ingredients:");
            System.err.println("DBG: - instance["+Saxon.xpath2string(config, "(nde:label)[1]", null, OpenSKOS.NAMESPACES)+"]");
            System.err.println("DBG: - api["+api+"]");
            System.err.println("DBG: - conceptScheme["+cs+"]");
            System.err.println("DBG: - match["+match+"]");
            
            // https://clavas.clarin.eu/clavas/public/api/find-concepts?q=prefLabel:*&conceptScheme=http://hdl.handle.net/11459/CLAVAS_810f8d2a-6723-3ba6-2e57-41d6d3844816&fl=uri,prefLabel&rows=100
            // check if the parameter value for conceptScheme is valid, otherwise default to general search for tenant and collection.
            // This was introduced because tenant beng has also expired conceptSchemes that never need to be consulted for NDE termennetwerk.
            // by adding collection parameter this is prevented, but then the tenant also has to be given.
            URL url = null;
            if ( cs != null && !cs.isEmpty()) {
            	url = new URL(api+"/find-concepts?q="+match+"&scheme="+cs);//+"&fl=uri,prefLabel,altLabel"

            } else {
		        String collection  = Saxon.xpath2string(config, "nde:collection", null, OpenSKOS.NAMESPACES);
		        String tenant  = Saxon.xpath2string(config, "nde:tenant", null, OpenSKOS.NAMESPACES);
	            System.err.println("DBG: - tenant["+tenant+"]");
	            System.err.println("DBG: - collection["+collection+"]");
            	url = new URL(api+"/find-concepts?q="+match+"&tenant="+tenant+"&collection="+collection);//+"&fl=uri,prefLabel,altLabel"
            }

            System.err.println("DBG: = url["+url+"]");
            XdmNode res = Saxon.buildDocument(new StreamSource(url.toString()));
            for (Iterator<XdmItem> iter = Saxon.xpathIterator(res, "/rdf:RDF/rdf:Description",null, OpenSKOS.NAMESPACES); iter.hasNext();) {
                XdmItem item = iter.next();
                TermDTO term = new TermDTO();
                term.uri = new URI(Saxon.xpath2string(item, "@rdf:about", null, OpenSKOS.NAMESPACES));
                // properties
                for (Iterator<XdmItem> lblIter = Saxon.xpathIterator(item, "skos:prefLabel",null, OpenSKOS.NAMESPACES); lblIter.hasNext();) {
                    term.prefLabel.add(lblIter.next().getStringValue());
                }
                for (Iterator<XdmItem> lblIter = Saxon.xpathIterator(item, "skos:altLabel",null, OpenSKOS.NAMESPACES); lblIter.hasNext();) {
                    term.altLabel.add(lblIter.next().getStringValue());
                }
                for (Iterator<XdmItem> lblIter = Saxon.xpathIterator(item, "skos:definition",null, OpenSKOS.NAMESPACES); lblIter.hasNext();) {
                    term.definition.add(lblIter.next().getStringValue());
                }
                for (Iterator<XdmItem> lblIter = Saxon.xpathIterator(item, "skos:scopeNote",null, OpenSKOS.NAMESPACES); lblIter.hasNext();) {
                    term.scopeNote.add(lblIter.next().getStringValue());
                }
                // Semantic relations
                for (Iterator<XdmItem> srIter = Saxon.xpathIterator(item, "skos:broader",null, OpenSKOS.NAMESPACES); srIter.hasNext();) {
                    URI broaderUri = new URI(Saxon.xpath2string(srIter.next(), "@rdf:resource", null, OpenSKOS.NAMESPACES));
                    RefDTO ref = new RefDTO();
                    ref.url=broaderUri.toString();
                    ref.label="";
                    term.broader.add(ref);
                }
                for (Iterator<XdmItem> srIter = Saxon.xpathIterator(item, "skos:narrower",null, OpenSKOS.NAMESPACES); srIter.hasNext();) {
                    URI narrowerUri = new URI(Saxon.xpath2string(srIter.next(), "@rdf:resource", null, OpenSKOS.NAMESPACES));
                    RefDTO ref = new RefDTO();
                    ref.url=narrowerUri.toString();
                    ref.label="";
                    term.narrower.add(ref);
                }
                for (Iterator<XdmItem> srIter = Saxon.xpathIterator(item, "skos:related",null, OpenSKOS.NAMESPACES); srIter.hasNext();) {
                    URI relatedUri = new URI(Saxon.xpath2string(srIter.next(), "@rdf:resource", null, OpenSKOS.NAMESPACES));
                    RefDTO ref = new RefDTO();
                    ref.url=relatedUri.toString();
                    ref.label="";
                    term.related.add(ref);
                }
                terms.add(term);
            }
        } catch (SaxonApiException | MalformedURLException | URISyntaxException ex) {
            Logger.getLogger(OpenSKOS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return terms;
    }
    
}
