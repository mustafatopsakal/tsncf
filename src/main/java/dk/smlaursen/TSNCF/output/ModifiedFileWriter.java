package dk.smlaursen.TSNCF.output;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.smlaursen.TSNCF.Main;
import dk.smlaursen.TSNCF.application.TTApplication;
import dk.smlaursen.TSNCF.solver.Multicast;
import dk.smlaursen.TSNCF.solver.Solution;
import dk.smlaursen.TSNCF.solver.Unicast;

public class ModifiedFileWriter {

	private ModifiedFileWriter(){};

	/**@param sol the {@link Solution}
	 * @param f the {@link File}.*/
	public static void Output(Solution sol, String filePath){
		//Logger logger = LoggerFactory.getLogger(Main.class.getSimpleName());
		
		//	Writer writer = null;
	    FileWriter fWriter = null;

		try{
			//writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
			//writer.write(sol.getCost().toDetailedString()+"\n\n");
			fWriter = new FileWriter(filePath, true);
			fWriter.write(sol.getCost().toDetailedString()+"\n\n");
			for(Multicast r : sol.getRouting()){
				//Only output AVB applications
				/*
				if(r.getApplication() instanceof TTApplication){
					continue;
				}*/
				//writer.write(r+"\n");
				fWriter.write(r+" ");

				for(Unicast u : r.getUnicasts()){
					//writer.write("  "+u.getRoute()+"\n");
					fWriter.write(" Route -> "+u.getRoute());
				}
				fWriter.write("\n");

			}
			fWriter.write("\n---------------------------------\n");

		} catch (IOException e){

		} finally{
			try{fWriter.close();}catch(Exception ex){}
		}
	}
}
