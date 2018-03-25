package edu.utdallas.main;

import java.util.Iterator;
import java.util.Map;

import edu.utdallas.relational.Dom;
import edu.utdallas.relational.Rel;
import soot.ArrayType;
import soot.Body;
import soot.Local;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AnyNewExpr;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.Constant;
import soot.jimple.IdentityStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;

public class MyTransformer extends SceneTransformer {
    private final Dom<HeapAbstraction> heapAbstractionsDom;
    private final Dom<Local> localsDom;
    private final Dom<InvokeExpr> invocationsDom;
    private final Dom<SootMethod> methodsDom;
    private final Dom<SootField> fieldsDom;
    
    private final Rel move;
    private final Rel alloc;
    
    public MyTransformer() {
        this.heapAbstractionsDom = new Dom<>();
        this.heapAbstractionsDom.setName("H");
        this.localsDom = new Dom<>();
        this.localsDom.setName("V");
        this.invocationsDom = new Dom<>();
        this.invocationsDom.setName("I");
        this.methodsDom = new Dom<>();
        this.methodsDom.setName("M");
        this.fieldsDom = new Dom<>();
        this.fieldsDom.setName("F");
        
        this.move = new Rel();
        this.alloc = new Rel();
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        this.construct();
    }
    
    private void addMethod(SootMethod meth) {
        synchronized(this.methodsDom) {
            this.methodsDom.add(meth);
        }
    }
    
    private void addLocal(Local local) {
        synchronized(this.localsDom) {
            this.localsDom.add(local);
        }
    }
    
    private void addConstant(Constant constant) {
        synchronized(this.heapAbstractionsDom) {
            this.heapAbstractionsDom.add(new ConstantObject(constant));
        }
    }
    
    private void addAbstractObject(AnyNewExpr ane) {
        synchronized(this.heapAbstractionsDom) {
            this.heapAbstractionsDom.add(new AbstractHeapObject(ane));
        }
    }
    
    private void addInvocation(InvokeExpr ie) {
        synchronized(this.invocationsDom) {
            this.invocationsDom.add(ie);
        }
    }
    
    private void addField(SootField field) {
        synchronized(this.fieldsDom) {
            this.fieldsDom.add(field);
        }
    }
    
    private void construct() {
        System.out.print("\nCollecting domains...");
        Scene.v().getClasses().parallelStream().forEach(appClass -> {
            for (final SootField field : appClass.getFields()) {
                addField(field);
            }
            for (final SootMethod appMethod : appClass.getMethods()) {
                /*TODO check if appMethod reachable from main, otherwise ignore it*/
                if (appMethod.isConcrete()) {
                    addMethod(appMethod);
                    Body body = appMethod.retrieveActiveBody();
                    for (final Local local : body.getLocals()) {
                        addLocal(local);
                    }
                    final Iterator<Unit> bit = body.getUnits().iterator();
                    while (bit.hasNext()) {
                        final Unit unit = bit.next();
                        if (unit instanceof AssignStmt) {
                            final Value rhsValue = ((AssignStmt) unit).getRightOp();
                            if (rhsValue instanceof AnyNewExpr) {
                               final Type type;
                               if (rhsValue instanceof NewArrayExpr) {
                                   type = ((NewArrayExpr) rhsValue).getBaseType();
                               } else if (rhsValue instanceof NewExpr) {
                                   type = rhsValue.getType();
                               } else { // NewMultiArrayExpr
                                   Type bt = ((NewMultiArrayExpr) rhsValue).getType();
                                   while (bt instanceof ArrayType) {
                                       bt = ((ArrayType) bt).getArrayElementType();
                                   }
                                   type = bt;
                               }
                               if (isStringType(type.toString())) {
                                   addAbstractObject((AnyNewExpr) rhsValue);
                               }
                            }
                            if (rhsValue instanceof StringConstant || rhsValue instanceof ClassConstant) {
                                addConstant((Constant) rhsValue);
                            }
                        } 
                        final InvokeExpr ie = getInvokeExpr(unit);
                        if (ie != null) {
                            for (Value arg : ie.getArgs()) {
                                if (arg instanceof StringConstant || arg instanceof ClassConstant) {
                                    addConstant((Constant) arg);
                                }
                            }
                            addInvocation(ie); // we are going to use them in CHA
                        }
                    }
                }
            }
        });
        try {
            this.heapAbstractionsDom.save(".", true);
            this.invocationsDom.save(".", true);
            this.localsDom.save(".", true);
            this.methodsDom.save(".", true);
        } catch (Exception e) {
            System.out.println("\t[FAILED]");
            e.printStackTrace();
            System.exit(0);
        }
        System.out.println("\t[OK]");
        System.out.print("\nCollecting relations...");
        
        this.move.setName("move");
        this.move.setSign("V0,V1", "V0xV1");
        this.move.setDoms(new Dom[] {this.localsDom, this.localsDom});
        this.move.zero();
        
        this.alloc.setName("alloc");
        this.alloc.setSign("M0,V0,H0", "M0xV0xH0");
        this.alloc.setDoms(new Dom[] {this.methodsDom, this.localsDom, this.heapAbstractionsDom});
        this.alloc.zero();
        
        Scene.v().getClasses().parallelStream().forEach(appClass -> {
            for (final SootMethod appMethod : appClass.getMethods()) {
                if (appMethod.isConcrete()) {
                    final Iterator<Unit> bit = appMethod.retrieveActiveBody().getUnits().iterator();
                    while (bit.hasNext()) {
                        final Unit unit = bit.next();
                        if (moveAssingment(unit)) {
                            final Local lhs = (Local) ((AssignStmt) unit).getLeftOp();
                            final Local rhs = (Local) ((AssignStmt) unit).getRightOp();
                            addMove(lhs, rhs);
                        } else if (allocAssignment(unit)) {
                            final Local lhsLocal = (Local) ((AssignStmt) unit).getLeftOp();
                            final Value rhsValue = ((AssignStmt) unit).getRightOp();
                            
                        }
                    }
                }
            }
        });
        try {
            this.move.save(".");
            this.alloc.save(".");
        } catch (Exception e) {
            System.out.println("\t[FAILED]");
            e.printStackTrace();
            System.exit(0);
        }
        System.out.println("\t[OK]");
    }
    
    private void addMove(Local l0, Local l1) {
        synchronized(this.move) {
            this.move.add(l0, l1);
        }
    }
    
    private void addAlloc(SootMethod m, Local l, AnyNewExpr ane) {
        synchronized(this.alloc) {
            this.alloc.add(m, l, ane);
        }
    }
    
    private boolean moveAssingment(Unit u) {
        return u instanceof AssignStmt 
                && ((AssignStmt) u).getLeftOp() instanceof Local
                && ((AssignStmt) u).getRightOp() instanceof Local; 
    }
    
    private boolean allocAssignment(Unit u) {
        if (!(u instanceof AssignStmt)) {
            return false;
        }
        final Value rhsValue = ((AssignStmt) u).getRightOp();
        return ((AssignStmt) u).getLeftOp() instanceof Local
                && (rhsValue instanceof StringConstant 
                        || rhsValue instanceof ClassConstant 
                        || rhsValue instanceof AnyNewExpr); 
    }
    
    private boolean isStringOps(SootMethod ops) {
        return ops.getName().matches("append|toString|<init>") 
                && isStringType(ops.getDeclaringClass().getJavaStyleName());
                    
    }
    
    private boolean isStringType(String typeName) {
        return typeName.matches("java\\.lang\\.String|java\\.lang\\.StringBuilder|java\\.lang\\.StringBuffer");
    }
    
    private InvokeExpr getInvokeExpr(Unit u) {
        if (u instanceof InvokeStmt) {
            return ((InvokeStmt) u).getInvokeExpr();
        } else if (u instanceof AssignStmt && ((AssignStmt) u).containsInvokeExpr()) {
            return ((AssignStmt) u).getInvokeExpr();
        }
        return null;
    }
    
}


//final CallGraph cg = Scene.v().getCallGraph();
//final int numberOfNodes;
//{
//  final Set<MethodOrMethodContext> methods = new HashSet<>();
//  final Iterator<MethodOrMethodContext> smi = cg.sourceMethods();
//  while (smi.hasNext()) {
//      final MethodOrMethodContext m = smi.next(); 
//      methods.add(m);
//      final Iterator<Edge> ei = cg.edgesOutOf(m);
//      while (ei.hasNext()) {
//          final Edge e = ei.next();
//          if (!methods.contains(e.getTgt())) {
//              methods.add(e.getTgt());
//          }
//      }
//  }
//  numberOfNodes = methods.size();
//}
////try (PrintWriter pw = new PrintWriter((new Date()).toString())) {
//  System.out.println(String.format("Number of Nodes: %d", numberOfNodes));
//  System.out.println(String.format("Number of Edges: %d", cg.size()));
////} catch (Exception e) {
////  
////}
