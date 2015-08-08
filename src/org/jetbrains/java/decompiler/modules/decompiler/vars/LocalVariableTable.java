package org.jetbrains.java.decompiler.modules.decompiler.vars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocalVariableTable {
  private Map<Integer, Set<LVTVariable>> startpoints;
  private ArrayList<LVTVariable> allLVT;
  private Map<VarVersionPair, String> mapVarNames;
private Map<Integer, List<LVTVariable>> mapLVT;

  public LocalVariableTable(int len) {
    startpoints = new HashMap<Integer,Set<LVTVariable>>(len);
    allLVT = new ArrayList<LVTVariable>(len);
  }

  public void addVariable(LVTVariable v) {
    allLVT.add(v);
    v.addTo(startpoints);
  }

  public void mergeLVTs(LocalVariableTable otherLVT) {
   for (LVTVariable other : otherLVT.allLVT) {
      int idx = allLVT.indexOf(other);
      if (idx < 0) {
        allLVT.add(other);
      }
      else {
        LVTVariable mine = allLVT.get(idx);
        mine.merge(other);
      }
    }
    mapVarNames = null; // Invalidate the cache and rebuild it.
  }

  public LVTVariable find(Integer index, List<Integer> offsets) {
    for (Integer offset : offsets) {
      Set<LVTVariable> lvs = startpoints.get(offset);
      if (lvs == null || lvs.isEmpty())
        continue;
      int idx = index.intValue();

      for (LVTVariable lv : lvs) {
        if (lv.index == idx)
          return lv;
      }
    }
    return null;
  }

  public Map<VarVersionPair, String> getMapVarNames() {
    if (mapVarNames == null)
      buildNameMap();
    return mapVarNames;
  }

  private void buildNameMap() {
    Map<Integer, Integer> versions = new HashMap<Integer, Integer>();
    mapVarNames = new HashMap<VarVersionPair, String>();
    mapLVT = new HashMap<Integer,List<LVTVariable>>();
    for (LVTVariable lvt : allLVT) {
      Integer idx = versions.get(lvt.index);
      if (idx == null)
        idx = 0;
      else
        idx++;
      versions.put(lvt.index, idx);
      List<LVTVariable> lvtList = mapLVT.get(lvt.index);
      if (lvtList == null) {
          lvtList = new ArrayList<LVTVariable>();
          mapLVT.put(lvt.index, lvtList);
      }
      lvtList.add(lvt);
      mapVarNames.put(new VarVersionPair(lvt.index, idx.intValue()), lvt.name);
    }
  }

public List<LVTVariable> getCandidates(int index) {
    return mapLVT.get(index);
}
}