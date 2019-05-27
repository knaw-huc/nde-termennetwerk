package nl.knaw.huc.di.nde;

import com.google.common.collect.Lists;

import java.net.URI;
import java.util.List;

public class TermDTO {
  public URI uri;
  public List<String> prefLabel = Lists.newArrayList();
  public List<String> altLabel = Lists.newArrayList();
  public List<String> scopeNote = Lists.newArrayList();
  public List<String> definition = Lists.newArrayList();
  public List<String> broader = Lists.newArrayList();
  public List<String> narrower = Lists.newArrayList();
  public List<String> related = Lists.newArrayList();
}
