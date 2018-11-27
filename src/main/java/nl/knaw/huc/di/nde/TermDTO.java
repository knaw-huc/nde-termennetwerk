package nl.knaw.huc.di.nde;

import java.util.List;

public class TermDTO {
    
    private List<String> prefLabels;
    private List<String> altLabels;
    private List<String> scopeNotes;
    private List<String> definitions;
    
    List<String> getPrefLabelsList() {
        return prefLabels;
    }

    List<String> getAltLabelsList() {
        return altLabels;
    }

    List<String> getScopeNotesList() {
        return scopeNotes;
    }

    List<String> getDefinitionsList() {
        return definitions;
    }
}
