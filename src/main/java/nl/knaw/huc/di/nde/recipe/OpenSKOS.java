package nl.knaw.huc.di.nde.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import nl.knaw.huc.di.nde.Registry;
import nl.knaw.huc.di.nde.TermDTO;
import nl.mpi.tla.util.Saxon;

/**
 *
 * @author menzowi
 */
public class OpenSKOS implements RecipeInterface {

    @Override
    public List<TermDTO> fetchMatchingTerms(XdmItem config, String match) {
        try {
            System.err.println("DBG: Lets cook some OpenSKOS!");
            System.err.println("DBG: Ingredients:");
            System.err.println("DBG: - instance["+Saxon.xpath2string(config, "(nde:label)[1]", null, Registry.NAMESPACES)+"]");
            System.err.println("DBG: - api["+Saxon.xpath2string(config, "nde:api", null, Registry.NAMESPACES)+"]");
            System.err.println("DBG: - conceptScheme["+Saxon.xpath2string(config, "nde:conceptScheme", null, Registry.NAMESPACES)+"]");
            System.err.println("DBG: - match["+match+"]");
        } catch (SaxonApiException ex) {
            Logger.getLogger(OpenSKOS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new ArrayList<TermDTO>();
    }
    
}
