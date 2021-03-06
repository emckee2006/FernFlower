/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler.modules.decompiler.exps;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;
import org.jetbrains.java.decompiler.main.collectors.BytecodeMappingTracer;
import org.jetbrains.java.decompiler.main.collectors.CounterContainer;
import org.jetbrains.java.decompiler.modules.decompiler.vars.CheckTypesResult;
import org.jetbrains.java.decompiler.modules.decompiler.vars.VarVersionPair;
import org.jetbrains.java.decompiler.struct.gen.VarType;
import org.jetbrains.java.decompiler.struct.match.IMatchable;
import org.jetbrains.java.decompiler.struct.match.MatchEngine;
import org.jetbrains.java.decompiler.struct.match.MatchNode;
import org.jetbrains.java.decompiler.struct.match.MatchNode.RuleValue;

public abstract class Exprent implements IMatchable {

  public static final int MULTIPLE_USES = 1;
  public static final int SIDE_EFFECTS_FREE = 2;
  public static final int BOTH_FLAGS = 3;

  public static final int EXPRENT_ARRAY = 1;
  public static final int EXPRENT_ASSIGNMENT = 2;
  public static final int EXPRENT_CONST = 3;
  public static final int EXPRENT_EXIT = 4;
  public static final int EXPRENT_FIELD = 5;
  public static final int EXPRENT_FUNCTION = 6;
  public static final int EXPRENT_IF = 7;
  public static final int EXPRENT_INVOCATION = 8;
  public static final int EXPRENT_MONITOR = 9;
  public static final int EXPRENT_NEW = 10;
  public static final int EXPRENT_SWITCH = 11;
  public static final int EXPRENT_VAR = 12;
  public static final int EXPRENT_ANNOTATION = 13;
  public static final int EXPRENT_ASSERT = 14;

  public final int type;
  public final int id;
  public BitSet bytecode = null;  // offsets of bytecode instructions decompiled to this exprent

  public Exprent(int type) {
    this.type = type;
    this.id = DecompilerContext.getCounterContainer().getCounterAndIncrement(CounterContainer.EXPRENT_COUNTER);
  }

  public int getPrecedence() {
    return 0; // the highest precedence
  }

  public VarType getExprType() {
    return VarType.VARTYPE_VOID;
  }

  public int getExprentUse() {
    return 0;
  }

  public CheckTypesResult checkExprTypeBounds() {
    return new CheckTypesResult();
  }

  public boolean containsExprent(Exprent exprent) {
    List<Exprent> listTemp = new ArrayList<Exprent>(getAllExprents(true));
    listTemp.add(this);

    for (Exprent lstExpr : listTemp) {
      if (lstExpr.equals(exprent)) {
        return true;
      }
    }

    return false;
  }

  public List<Exprent> getAllExprents(boolean recursive) {
    List<Exprent> lst = getAllExprents();
    if (recursive) {
      for (int i = lst.size() - 1; i >= 0; i--) {
        lst.addAll(lst.get(i).getAllExprents(true));
      }
    }
    return lst;
  }

  public Set<VarVersionPair> getAllVariables() {
    List<Exprent> lstAllExprents = getAllExprents(true);
    lstAllExprents.add(this);

    Set<VarVersionPair> set = new HashSet<VarVersionPair>();
    for (Exprent expr : lstAllExprents) {
      if (expr.type == EXPRENT_VAR) {
        set.add(new VarVersionPair((VarExprent)expr));
      }
    }
    return set;
  }

  public List<Exprent> getAllExprents() {
    throw new RuntimeException("not implemented");
  }

  public Exprent copy() {
    throw new RuntimeException("not implemented");
  }

  public TextBuffer toJava(int indent, BytecodeMappingTracer tracer) {
    throw new RuntimeException("not implemented");
  }

  public void replaceExprent(Exprent oldExpr, Exprent newExpr) { }

  public void addBytecodeOffsets(BitSet bytecodeOffsets) {
    if (bytecodeOffsets != null) {
      if (bytecode == null) {
        bytecode = new BitSet();
      }
      bytecode.or(bytecodeOffsets);
    }
  }

  public abstract void getBytecodeRange(BitSet values);

  protected void measureBytecode(BitSet values) {
    if (bytecode != null)
      values.or(bytecode);
  }
  protected static void measureBytecode(BitSet values, Exprent exprent) {
    if (exprent != null)
      exprent.getBytecodeRange(values);
  }
  protected static void measureBytecode(BitSet values, List<Exprent> list) {
    if (list != null && !list.isEmpty()) {
      for (Exprent e : list)
        e.getBytecodeRange(values);
    }
  }

  // *****************************************************************************
  // IMatchable implementation
  // *****************************************************************************

  public IMatchable findObject(MatchNode matchNode, int index) {

    if(matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return null;
    }

    List<Exprent> lstAllExprents = getAllExprents();

    if(lstAllExprents == null || lstAllExprents.isEmpty()) {
      return null;
    }

    String position = (String)matchNode.getRuleValue(MatchProperties.EXPRENT_POSITION);
    if(position != null) {
      if(position.matches("-?\\d+")) {
        return lstAllExprents.get((lstAllExprents.size() + Integer.parseInt(position)) % lstAllExprents.size()); // care for negative positions
      }
    } else if(index < lstAllExprents.size()) { // use 'index' parameter
      return lstAllExprents.get(index);
    }

    return null;
  }

  public boolean match(MatchNode matchNode, MatchEngine engine) {

    if(matchNode.getType() != MatchNode.MATCHNODE_EXPRENT) {
      return false;
    }

    for(Entry<MatchProperties, RuleValue> rule : matchNode.getRules().entrySet()) {
      switch(rule.getKey()) {
      case EXPRENT_TYPE:
        if(this.type != ((Integer)rule.getValue().value).intValue()) {
          return false;
        }
        break;
      case EXPRENT_RET:
        if(!engine.checkAndSetVariableValue((String)rule.getValue().value, this)) {
          return false;
        }
        break;
      }

    }

    return true;
  }

  public static List<Exprent> sortIndexed(List<Exprent> lst) {
    List<Exprent> ret = new ArrayList<Exprent>();
    List<VarExprent> defs = new ArrayList<VarExprent>();

    Comparator<VarExprent> comp = new Comparator<VarExprent>() {
      public int compare(VarExprent o1, VarExprent o2) {
        return o1.getIndex() - o2.getIndex();
      }
    };

    for (Exprent exp : lst) {
      boolean isDef = exp instanceof VarExprent && ((VarExprent)exp).isDefinition();
      if (!isDef) {
        if (defs.size() > 0) {
          Collections.sort(defs, comp);
          ret.addAll(defs);
          defs.clear();
        }
        ret.add(exp);
      }
      else {
        defs.add((VarExprent)exp);
      }
    }

    if (defs.size() > 0) {
      Collections.sort(defs, comp);
      ret.addAll(defs);
    }
    return ret;
  }
}
