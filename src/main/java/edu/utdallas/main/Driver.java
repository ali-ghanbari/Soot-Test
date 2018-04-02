package edu.utdallas.main;

import java.util.ArrayList;
import java.util.List;

import edu.utdallas.relational.Dom;
import edu.utdallas.relational.Rel;
import edu.utdallas.relational.RelSign;
import soot.PackManager;
import soot.Transform;
import soot.options.Options;

public class Driver {
//    private static String join(String[] paths) {
//        
//    }
    
    public static void main(String[] args) {
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.myTransformer", new MyTransformer()));
        Options.v().set_whole_program(true);
        Options.v().set_main_class("edu.utdallas.main.Main");//org.apache.commons.math4.MockMain");
        Options.v().set_soot_classpath("/usr/lib/jvm/jdk1.7.0_80/jre/lib/rt.jar:/usr/lib/jvm/jdk1.7.0_80/jre/lib/jce.jar:/home/ali/eclipse-workspace/subject-prog/target/classes/");// /home/ali/CP/commons-math/target/classes/:/home/ali/CP/commons-math/target/test-classes/:/home/ali/CP/junit-4.11.jar:/home/ali/CP/hamcrest-all-1.3.jar");
        Dom<Integer> N = new Dom<>();
        N.setName("N");
        N.add(10);
        RelSign rs = new RelSign(new String[] {"N0"}, "N0");
        Rel r1 = new Rel();
        r1.setName("nn");
        r1.setSign(rs);
        r1.setDoms(new Dom[] {N});
        r1.zero();
        
        
        N.indexOf(10);
        r1.add(N.indexOf(10));
//        List<String> excludeList = new ArrayList<>();
//        String[] packs = new String[] {"org.apache.commons.math4.*"};
//        for (String p : packs) {
//            excludeList.add(p);
//        }
//        //Options.v().set_exclude(excludeList);
        Options.v().set_app(true);
        Options.v().set_output_format(Options.output_format_jimple);
//        Options.v().set_include(excludeList);
//        Options.v().set_no_bodies_for_excluded(true);
//        System.out.println(Options.v().include());
        soot.Main.v().run(new String[] {"edu.utdallas.main.Main"});
    }
}
/**
 * 1. CHA on all benchmark program + measure timing+number of nodes, edges, and possibly sccs.
 * 1.5. Compare 0-CFA to mvn test
 * 2. Get dynamic loading+reflection information+impact to the results+for comparison purposes.
 * 3. Read and analyze the paper
 */

/**
 * --w --cp /home/ali/CP/junit-4.11.jar
 * :/home/ali/CP/commons-lang3-3.7.jar
 * :/home/ali/CP/dexmaker-1.5.jar
 * :/home/ali/CP/slf4j-api-1.8.0-beta1.jar
 * :/home/ali/CP/log4j-1.2.17.jar
 * :/home/ali/CP/easymock-3.5.1.jar
 * :/home/ali/CP/portlet-api-3.0.0.jar
 * :/home/ali/CP/commons-io-2.4.jar
 * :/home/ali/CP/javax.servlet-api-3.0.1.jar
 * :/home/ali/CP/hamcrest-all-1.3.jar
 * :/home/ali/CP/mockito-all-1.10.19.jar
 * :/home/ali/subject-programs/commons-math/target/classes/
 * :/home/ali/subject-programs/commons-math/target/test-classes/
 * :/usr/lib/jvm/jdk1.7.0_80/jre/lib/rt.jar
 * :/usr/lib/jvm/jdk1.7.0_80/jre/lib/jce.jar org.apache.commons.math4.MockMain
 */
