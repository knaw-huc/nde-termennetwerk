package nl.knaw.huc.di.nde;

import com.google.common.collect.Lists;
import java.util.List;

public class DatasetDTO {
    public String dataset;
    public List<String>  label = Lists.newArrayList();
    public List<TermDTO> terms = Lists.newArrayList();
 }
