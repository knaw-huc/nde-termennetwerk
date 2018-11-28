package nl.knaw.huc.di.nde.recipe;

import java.util.List;
import net.sf.saxon.s9api.XdmItem;
import nl.knaw.huc.di.nde.TermDTO;

public interface RecipeInterface {
    
    List<TermDTO> fetchMatchingTerms(XdmItem config, String match);
    
}
