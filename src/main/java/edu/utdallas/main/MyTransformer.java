package edu.utdallas.main;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import soot.ValueBox;
import soot.jimple.AnyNewExpr;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

public class MyTransformer extends SceneTransformer {
    private final Dom<HeapAbstraction> heapAbstractionsDom;
    private final Dom<Local> localsDom;
    private final Dom<InvokeExpr> invocationsDom;
    private final Dom<SootMethod> methodsDom;
    private final Dom<SootField> fieldsDom;
    private final Dom<Type> typesDom;
    private final Dom<MethodSignature> signaturesDom;
    private final Dom<Integer> natural;
    private final Dom<String> methNamesDom;
    
    private final Rel vardef; // local variable definition: v = null; v = "string constant";
    private final Rel sfdef; // static field definition: ClassName.field = null; ClassName.field = "string constant";
    private final Rel ifdef; // instance field definition: v.field = null; v.field = "string constant";
    private final Rel paramdef; // parameter definition through calls: ...method(.., "string constant", ...)...
    private final Rel retdef; // return definition: return null; return "string constant"; 
    private final Rel move;
    private final Rel ifload;
    private final Rel sfload;
    private final Rel ifstore;
    private final Rel sfstore;
    private final Rel formal;
    private final Rel actual;
    private final Rel fret;
    private final Rel aret;
    private final Rel maycall;
    private final Rel rcvar;
    private final Rel self;
    private final Rel name;
    private final Rel strfac;
    
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
        this.typesDom = new Dom<>();
        this.typesDom.setName("T");
        this.signaturesDom = new Dom<>();
        this.signaturesDom.setName("S");
        this.natural = new Dom<>();
        this.natural.setName("N");
        this.methNamesDom = new Dom<>();
        this.methNamesDom.setName("A");
        
        this.move = new Rel();
        this.ifload = new Rel();
        this.sfload = new Rel();
        this.ifstore = new Rel();
        this.sfstore = new Rel();
        this.vardef = new Rel();
        this.sfdef = new Rel();
        this.ifdef = new Rel();
        this.paramdef = new Rel();
        this.formal = new Rel();
        this.actual = new Rel();
        this.retdef = new Rel();
        this.fret = new Rel();
        this.aret = new Rel();
        this.maycall = new Rel();
        this.rcvar = new Rel();
        this.self = new Rel();
        this.name = new Rel();
        this.strfac = new Rel();
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
    
    private void addType(Type type) {
        synchronized(this.typesDom) {
            this.typesDom.add(type);
        }
    }
    
    private void addSignature(MethodSignature ms) {
        synchronized(this.signaturesDom) {
            this.signaturesDom.add(ms);
        }
    }
    
    private void addMethName(String mn) {
        synchronized(this.methNamesDom) {
            this.methNamesDom.add(mn);
        }
    }
    
    private void construct() {
        System.out.print("\nCollecting domains...");
        
        for (int i = 0; i < 256; i++) {
            this.natural.add(i);
        }
        
        this.heapAbstractionsDom.add(Epsilon.EPSILON);
        
        Scene.v().getClasses().parallelStream().forEach(theClass -> {
            addType(theClass.getType());
            for (final SootField field : theClass.getFields()) {
                addField(field);
            }
            for (final SootMethod method : theClass.getMethods()) {
                addMethName(method.getName());
                addMethod(method);
                addSignature(new MethodSignature(method));
            }
        });
        
        final Set<SootMethod> appConcMethods = Scene.v().getApplicationClasses().parallelStream()
                .map(SootClass::getMethods)
                .flatMap(List::stream)
                .filter(SootMethod::isConcrete)
                .collect(Collectors.toSet()); 
        
        appConcMethods.parallelStream().forEach(method -> {
            final Body body = method.retrieveActiveBody();
            for (final Local local : body.getLocals()) {
                addLocal(local);
            }
            final Iterator<Unit> bit = body.getUnits().iterator();
            while (bit.hasNext()) {
                final Unit unit = bit.next();
                if (unit instanceof AssignStmt) {
                    final Value rhsValue = ((AssignStmt) unit).getRightOp();
                    if (rhsValue instanceof AnyNewExpr) {
                        final Type type = getBaseType((AnyNewExpr) rhsValue);
                        if (isStringType(type.toString())) {
                            addAbstractObject((AnyNewExpr) rhsValue);
                        }
                    }
                    if (rhsValue instanceof Constant) {
                        addConstant((Constant) rhsValue);
                    }
                } else if (unit instanceof ReturnStmt) {
                    Value op = ((ReturnStmt) unit).getOp();
                    if (op instanceof Constant) {
                        addConstant((Constant) op);
                    }
                }
                final InvokeExpr ie = getInvokeExpr(unit);
                if (ie != null) {
                    for (Value arg : ie.getArgs()) {
                        if (arg instanceof Constant) {
                            addConstant((Constant) arg);
                        }
                    }
                    addInvocation(ie); // we are going to use them in CHA
                }
            }
        });
        try {
            this.heapAbstractionsDom.save(".", true);
            this.invocationsDom.save(".", true);
            this.localsDom.save(".", true);
            this.methodsDom.save(".", true);
            this.fieldsDom.save(".", true);
            this.typesDom.save(".", true);
            this.signaturesDom.save(".", true);
            this.natural.save(".", true);
            this.methNamesDom.save(".", true);
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
       
        this.ifload.setName("ifload");
        this.ifload.setSign("V0,V1,F0", "V0xV1xF0");
        this.ifload.setDoms(new Dom[] {this.localsDom, this.localsDom, this.fieldsDom});
        this.ifload.zero();

        this.sfload.setName("sfload");
        this.sfload.setSign("V0,F0", "V0xF0");
        this.sfload.setDoms(new Dom[] {this.localsDom, this.fieldsDom});
        this.sfload.zero();
        
        this.ifstore.setName("ifstore");
        this.ifstore.setSign("V0,F0,V1", "V0xF0xV1");
        this.ifstore.setDoms(new Dom[] {this.localsDom, this.fieldsDom, this.localsDom});
        this.ifstore.zero();

        this.sfstore.setName("sfstore");
        this.sfstore.setSign("F0,V0", "F0xV0");
        this.sfstore.setDoms(new Dom[] {this.fieldsDom, this.localsDom});
        this.sfstore.zero();
        
        this.vardef.setName("vardef");
        this.vardef.setSign("V0,H0", "V0xH0");
        this.vardef.setDoms(new Dom[] {this.localsDom, this.heapAbstractionsDom});
        this.vardef.zero();
        
        this.sfdef.setName("sfdef");
        this.sfdef.setSign("F0,H0", "F0xH0");
        this.sfdef.setDoms(new Dom[] {this.fieldsDom, this.heapAbstractionsDom});
        this.sfdef.zero();
        
        this.ifdef.setName("ifdef");
        this.ifdef.setSign("V0,F0,H0", "V0xF0xH0");
        this.ifdef.setDoms(new Dom[] {this.localsDom, this.fieldsDom, this.heapAbstractionsDom});
        this.ifdef.zero();
        
        this.paramdef.setName("paramdef");
        this.paramdef.setSign("I0,N0,H0", "I0xN0xH0");
        this.paramdef.setDoms(new Dom[] {this.invocationsDom, this.natural, this.heapAbstractionsDom});
        this.paramdef.zero();
        
        this.retdef.setName("retdef");
        this.retdef.setSign("M0,H0", "M0xH0");
        this.retdef.setDoms(new Dom[] {this.methodsDom, this.heapAbstractionsDom});
        this.retdef.zero();
        
        this.formal.setName("formal");
        this.formal.setSign("M0,N0,V0", "M0xN0xV0");
        this.formal.setDoms(new Dom[] {this.methodsDom, this.natural, this.localsDom});
        this.formal.zero();
        
        this.actual.setName("actual");
        this.actual.setSign("I0,N0,V0", "I0xN0xV0");
        this.actual.setDoms(new Dom[] {this.invocationsDom, this.natural, this.localsDom});
        this.actual.zero();
        
        this.fret.setName("fret");
        this.fret.setSign("M0,V0", "M0xV0");
        this.fret.setDoms(new Dom[] {this.methodsDom, this.localsDom});
        this.fret.zero();
        
        this.aret.setName("aret");
        this.aret.setSign("I0,V0", "I0xV0");
        this.aret.setDoms(new Dom[] {this.invocationsDom, this.localsDom});
        this.aret.zero();
        
        this.maycall.setName("maycall");
        this.maycall.setSign("I0,M0", "I0xM0");
        this.maycall.setDoms(new Dom[] {this.invocationsDom, this.methodsDom});
        this.maycall.zero();
        
        this.rcvar.setName("rcvar");
        this.rcvar.setSign("I0,V0", "I0xV0");
        this.rcvar.setDoms(new Dom[] {this.invocationsDom, this.localsDom});
        this.rcvar.zero();
        
        this.self.setName("self");
        this.self.setSign("M0,V0", "M0xV0");
        this.self.setDoms(new Dom[] {this.methodsDom, this.localsDom});
        this.self.zero();
        
        this.name.setName("name");
        this.name.setSign("M0,A0", "M0xA0");
        this.name.setDoms(new Dom[] {this.methodsDom, this.methNamesDom});
        this.name.zero();
        
        this.strfac.setName("strfac");
        this.strfac.setSign("V0", "V0");
        this.strfac.setDoms(new Dom[] {this.localsDom});
        this.strfac.zero();
        
        final CallGraph cg = Scene.v().getCallGraph();
        
        appConcMethods.parallelStream().forEach(method -> {
            addMethName(method);
            final Body body = method.retrieveActiveBody();
            for (final Local local : body.getLocals()) {
                final Type type = local.getType();
                if (isStringFactoryType(type.toString())) {
                    addStrFactory(local);
                }
            }
            for (int i = 0; i < body.getParameterLocals().size(); i++) {
                addFormal(method, i, body.getParameterLocal(i));
                if (!method.isStatic()) {
                    addSelf(method, body.getThisLocal());
                }
            }
            final Iterator<Unit> bit = body.getUnits().iterator();
            while (bit.hasNext()) {
                final Unit unit = bit.next();
                final InvokeExpr ie = getInvokeExpr(unit);
                if (ie != null) {
                    final Iterator<Edge> outs = cg.edgesOutOf(unit);
                    while (outs.hasNext()) {
                        final SootMethod callee = outs.next().tgt();
                        if (appConcMethods.contains(callee)) {
                            addMayCall(ie, callee);
                        }
                    }
                }
                if (unit instanceof ReturnStmt) {
                    Value op = ((ReturnStmt) unit).getOp();
                    if (op instanceof Constant) {
                        addRetDef(method, new ConstantObject((Constant) op));
                    } else if (op instanceof Local) {
                        addFRet(method, (Local) op);
                    }
                } else if (unit instanceof InvokeStmt) {
                    if (!(ie instanceof StaticInvokeExpr)) {
                        final List<ValueBox> useBoxes = ie.getUseBoxes();
                        final Local rcvar = (Local) useBoxes.get(useBoxes.size() - 1).getValue();
                        addReceiverVar(ie, rcvar);
                    }
                    for (int i = 0; i < ie.getArgCount(); i ++) {
                        final Value arg = ie.getArg(i);
                        if (arg instanceof Constant) {
                            addParamDef(ie, i, new ConstantObject((Constant) arg));
                        } else if (arg instanceof Local) {
                            addActual(ie, i, (Local) arg);
                        }
                    }
                } else if (unit instanceof AssignStmt) {
                    final Value lhsValue = ((AssignStmt) unit).getLeftOp();
                    if (ie != null) {
                        addARet(ie, (Local) lhsValue);
                        for (int i = 0; i < ie.getArgCount(); i ++) {
                            final Value arg = ie.getArg(i);
                            if (arg instanceof Constant) {
                                addParamDef(ie, i, new ConstantObject((Constant) arg));
                            } else if (arg instanceof Local) {
                                addActual(ie, i, (Local) arg);
                            }
                        }
                    }
                    final Value rhsValue = ((AssignStmt) unit).getRightOp();
                    if (lhsValue instanceof Local) {
                        final Local lhsLocal = (Local) ((AssignStmt) unit).getLeftOp();
                        if (rhsValue instanceof Local) {
                            final Local rhsLocal = (Local) rhsValue;
                            addMove(lhsLocal, rhsLocal);
                        } else if (rhsValue instanceof Constant) {
                            addVarDef(lhsLocal, new ConstantObject((Constant) rhsValue));
                        } else if (rhsValue instanceof AnyNewExpr) {
                            final Type type = getBaseType((AnyNewExpr) rhsValue);
                            if (isStringType(type.toString())) {
                                addVarDef(lhsLocal, new AbstractHeapObject((AnyNewExpr) rhsValue));
                            }
                        } else if (rhsValue instanceof FieldRef) {
                            final FieldRef fr = (FieldRef) rhsValue;
                            final SootField field = fr.getField();
                            if (fr instanceof InstanceFieldRef) {
                                final Local base = (Local) fr.getUseBoxes().get(0).getValue();
                                addInstanceFieldLoad(lhsLocal, base, field);
                            } else {
                                addStaticFieldLoad(lhsLocal, field);
                            }
                        }
                    } else if (lhsValue instanceof FieldRef) {
                        final FieldRef fr = (FieldRef) lhsValue;
                        final SootField field = fr.getField();
                        if (fr instanceof InstanceFieldRef) {
                            final Local base = (Local) fr.getUseBoxes().get(0).getValue();
                            if (rhsValue instanceof Local) {
                                addInstanceFieldStore(base, field, (Local) rhsValue);
                            }  else if (rhsValue instanceof Constant) {
                                addIFDef(base, field, new ConstantObject((Constant) rhsValue));
                            }
                        } else { //static field reference
                            if (rhsValue instanceof Local) {
                                addStaticFieldStore(field, (Local) rhsValue);
                            } else if (rhsValue instanceof Constant) {
                                addSFDef(field, new ConstantObject((Constant) rhsValue));
                            }
                        }
                    }
                }
            }
        });
        try {
            this.move.save(".");
            this.ifload.save(".");
            this.sfload.save(".");
            this.ifstore.save(".");
            this.sfstore.save(".");
            this.vardef.save(".");
            this.sfdef.save(".");
            this.ifdef.save(".");
            this.paramdef.save(".");
            this.retdef.save(".");
            this.formal.save(".");
            this.actual.save(".");
            this.fret.save(".");
            this.aret.save(".");
            this.maycall.save(".");
            this.rcvar.save(".");
            this.self.save(".");
            this.name.save(".");
            this.strfac.save(".");
        } catch (Exception e) {
            System.out.println("\t[FAILED]");
            e.printStackTrace();
            System.exit(0);
        }
        System.out.println("\t[OK]");
    }
    
    private void addStrFactory(Local l) {
        synchronized (this.strfac) {
            this.strfac.add(l);
        }
    }
    
    private void addMethName(SootMethod method) {
        synchronized (this.name) {
            this.name.add(method, method.getName());
        }
    }
    
    private void addSelf(SootMethod method, Local tl) {
        synchronized (this.self) {
            this.self.add(method, tl);
        }
    }

    private void addReceiverVar(InvokeExpr ie, Local l) {
        synchronized (this.rcvar) {
            this.rcvar.add(ie, l);
        }
    }
    
    private void addMayCall(InvokeExpr ie, SootMethod callee) {
        synchronized (this.maycall) {
            this.maycall.add(ie, callee);
        }
    }
    
    private void addARet(InvokeExpr ie, Local local) {
        synchronized (this.aret) {
            this.aret.add(ie, local);
        }
    }
    
    private void addFRet(SootMethod method, Local local) {
        synchronized (this.fret) {
            this.fret.add(method, local);
        }
    }
    
    private void addRetDef(SootMethod method, HeapAbstraction ha) {
        synchronized (this.retdef) {
            this.retdef.add(method, ha);
        }
    }
    
    private void addActual(InvokeExpr ie, int n, Local arg) {
        synchronized (this.actual) {
            this.actual.add(ie, n, arg);
        }
    }
    
    private void addFormal(SootMethod method, int n, Local param) {
        synchronized (this.formal) {
            this.formal.add(method, n, param);
        }
    }
    
    private void addParamDef(InvokeExpr ie, int n, HeapAbstraction ha) {
        synchronized (this.paramdef) {
            this.paramdef.add(ie, n, ha);
        }
    }
    
    private void addIFDef(Local base, SootField field, HeapAbstraction ha) {
        synchronized (this.ifdef) {
            this.ifdef.add(base, field, ha);
        }
    }
    
    private void addVarDef(Local lhsLocal, HeapAbstraction ha) {
        synchronized (this.vardef) {
            this.vardef.add(lhsLocal, ha);
        }
    }
    
    private void addSFDef(SootField field, HeapAbstraction ha) {
        synchronized (this.sfdef) {
            this.sfdef.add(field, ha);
        }
    }
    
    private void addInstanceFieldStore(Local base, SootField field, Local from) {
        synchronized (this.ifstore) {
            this.ifstore.add(base, field, from);
        }
    }
    
    private void addStaticFieldStore(SootField field, Local from) {
        synchronized (this.sfstore) {
            this.sfstore.add(field, from);
        }
    }
    
    private void addInstanceFieldLoad(Local to, Local base, SootField field) {
        synchronized (this.ifload) {
            this.ifload.add(to, base, field);
        }
    }
    
    private void addStaticFieldLoad(Local to, SootField field) {
        synchronized (this.sfload) {
            this.sfload.add(to, field);
        }
    }
    
    private Type getBaseType(AnyNewExpr ane) {
        final Type type;
        if (ane instanceof NewArrayExpr) {
            type = ((NewArrayExpr) ane).getBaseType();
        } else if (ane instanceof NewExpr) {
            type = ane.getType();
        } else { // NewMultiArrayExpr
            Type bt = ((NewMultiArrayExpr) ane).getType();
            while (bt instanceof ArrayType) {
                bt = ((ArrayType) bt).getArrayElementType();
            }
            type = bt;
        }
        return type;
    }
    
    private void addMove(Local lhs, Local rhs) {
        synchronized(this.move) {
            this.move.add(lhs, rhs);
        }
    }
//    
//    private void addAlloc(SootMethod m, Local l, HeapAbstraction ha) {
//        synchronized(this.alloc) {
//            this.alloc.add(m, l, ha);
//        }
//    }
//    
//    private boolean isTrackable(Type type) {
//        final String typeName = type.toString();
//        return isStringType(typeName) || typeName.matches("java\\.lang\\.Class"); 
//    }
//        
    private boolean isStringType(String typeName) {
        return typeName.matches("java\\.lang\\.String|java\\.lang\\.StringBuilder|java\\.lang\\.StringBuffer");
    }
    
    private boolean isStringFactoryType(String typeName) {
        return typeName.matches("java\\.lang\\.StringBuilder|java\\.lang\\.StringBuffer");
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


//private boolean isStringOps(SootMethod ops) {
//return ops.getName().matches("append|toString|<init>") 
//      && isStringType(ops.getDeclaringClass().getJavaStyleName());
//          
//}

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
