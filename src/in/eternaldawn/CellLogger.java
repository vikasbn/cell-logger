/*
 * Created by: Martin Sauter, martin.sauter@wirelessmoves.com
 * Modified by: Vikas B N, vikas@eternaldawn.in
 *
 * Copyright (c) 2011 Martin Sauter. All Rights Reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, 
 * MA 02111-1307, USA
 */

package in.eternaldawn;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import android.content.Context;
import android.content.DialogInterface;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;


public class CellLogger extends Activity {
    /* menu item id's */
    private static final int RESET_COUNTER = 0;
    private static final int ABOUT = 1;
    private static final int TOGGLE_DEBUG = 2;
  
    /* name of the output file */
    private String filename = "cell-log-data.txt";
  
    /* These variables need to be global, so we can used them onResume and onPause method to
       stop the listener */
    private TelephonyManager Tel;
    private MyPhoneStateListener MyListener;
    private boolean isListenerActive = false;
  
    /*  These variables need to be global so they can be saved when the activity exits
     *  and reloaded upon restart.
     */
    private long NumberOfSignalStrengthUpdates = 0;
  
    private long LastCellId = 0;
    private long NumberOfCellChanges = -1;
  
    private long LastLacId = 0;
    private long NumberOfLacChanges = -1;
  
    private long PreviousCells[] = new long [4];
    private int  PreviousCellsIndex = 0;
    private long NumberOfUniqueCellChanges = -1;
  
    private boolean outputDebugInfo = false;
    
    /* Buffer string to cache file operations */
    private String FileWriteBufferStr = ""; 
    
    /* a resource required to keep the phone from going to the screen saver after a timeout */
    private PowerManager.WakeLock wl;

    /* This method is called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        /* If saved variable state exists from last run, recover it */
        if (savedInstanceState != null) {
        	NumberOfSignalStrengthUpdates = savedInstanceState.getLong("NumberOfSignalStrengthUpdates");
        	
        	LastCellId = savedInstanceState.getLong("LastCellId");
        	NumberOfCellChanges = savedInstanceState.getLong("NumberOfCellChanges");
        	
        	LastLacId = savedInstanceState.getLong("LastLacId");
        	NumberOfLacChanges = savedInstanceState.getLong("NumberOfLacChanges");
        	
        	PreviousCells = savedInstanceState.getLongArray("PreviousCells");
        	PreviousCellsIndex = savedInstanceState.getInt("PreviousCellsIndex");
        	NumberOfUniqueCellChanges = savedInstanceState.getLong("NumberOfUniqueCellChanges");
        	
        	outputDebugInfo = savedInstanceState.getBoolean("outputDebugInfo");
        	
        }
        else {
        	/* Initialize PreviousCells Array to defined values */
        	for (int x = 0; x < PreviousCells.length; x++) 
        		PreviousCells[x] = 0;
        }	
       
        /* Get a handle to the telephony manager service */
        /* A listener will be installed in the object from the onResume() method */
        MyListener = new MyPhoneStateListener();
        Tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
       
        /* get a handle to the power manager and set a wake lock so the screen saver
         * is not activated after a timeout */        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0, RESET_COUNTER, 0, "Rest Counters");
    	menu.add(0, ABOUT, 0, "About");
    	menu.add(0, TOGGLE_DEBUG, 0, "Toggle Debug Mode");
		return true;
    }
    
    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
    	Context context = getApplicationContext();
    	CharSequence debugEnabled = "Enabled Debug Logging";
    	CharSequence debugDisabled = "Disabled Debug Logging";
    	int duration = Toast.LENGTH_LONG;

    	switch (item.getItemId()) {
    	    case RESET_COUNTER:
    	    	
    	    	NumberOfCellChanges = 0;
                NumberOfLacChanges = 0;
                NumberOfSignalStrengthUpdates = 0;
                
                NumberOfUniqueCellChanges = 0;
                
            	/* Initialize PreviousCells Array to a defined value */
            	for (int x = 0; x < PreviousCells.length; x++) 
            		PreviousCells[x] = 0;
            	
       	        return true;
       	    
    	    case ABOUT:
    	    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	    	builder.setMessage("Cell Logger\r\n2011, Martin Sauter\r\nhttp://www.wirelessmoves.com\r\nSome modifications by Vikas B N")
    	    	       .setCancelable(false)
    	    	       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
    	    	           public void onClick(DialogInterface dialog, int id) {
    	    	        	   dialog.cancel();
    	    	           }
    	    	       });
    	    	       
    	    	 AlertDialog alert = builder.create();
    	    	 alert.show();
    	    	 
    	    	 return true;

    	    case TOGGLE_DEBUG:
    	    	/* Toggle the debug behavior of the program when the user selects this menu item */
    	    	if (outputDebugInfo == false) {
    	    		outputDebugInfo = true;
    	    		Toast.makeText(context, debugEnabled, duration).show();
    	    	}
    	    	else {
    	    		outputDebugInfo = false;
    	    		Toast.makeText(context, debugDisabled, duration).show();
    	    	}
    	    	
    	    	return true;
    	    		
    	    default:
    	        return super.onOptionsItemSelected(item);

    	}
    }

  
    @Override
	public void onBackPressed() {
      /* do nothing to prevent the user from accidentally closing the activity this way*/
    }
    
    /* Called when the application is minimized */
    @Override
    protected void onPause()
    {
      super.onPause();
      
      /* remove the listener object from the telephony manager as otherwise several listeners
       * will appear on some Android implementations once the application is resumed. 
       */
      Tel.listen(MyListener, PhoneStateListener.LISTEN_NONE);
      isListenerActive = false;
            
      /* let the device activate the screen lock again */
      wl.release();      
    }

    /* Called when the application resumes */
    @Override
    protected void onResume()
    {
       super.onResume();
       
       if (isListenerActive == false) {
           Tel.listen(MyListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
           isListenerActive = true; 
       }
       
       /* prevent the screen lock after a timeout again */
       wl.acquire();
    }
    
    /* Called when the activity closes or is sent to the background*/
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	    	
    	super.onSaveInstanceState(savedInstanceState);
    	      
        /* save variables */
    	savedInstanceState.putLong("NumberOfSignalStrengthUpdates", NumberOfSignalStrengthUpdates);
    	
    	savedInstanceState.putLong("LastCellId", LastCellId);
    	savedInstanceState.putLong("NumberOfCellChanges", NumberOfCellChanges);
    	
    	savedInstanceState.putLong("LastLacId", LastLacId);
    	savedInstanceState.putLong("NumberOfLacChanges", NumberOfLacChanges);
    	
    	savedInstanceState.putLongArray("PreviousCells", PreviousCells);
    	savedInstanceState.putInt("PreviousCellsIndex", PreviousCellsIndex);
    	savedInstanceState.putLong("NumberOfUniqueCellChanges", NumberOfUniqueCellChanges);
 	
        savedInstanceState.putBoolean("outputDebugInfo", outputDebugInfo);  

        /* save the trace data still in the write buffer into a file */
        saveDataToFile(FileWriteBufferStr, "---in save instance, " + DateFormat.getTimeInstance().format(new Date()) + "\r\n");
        FileWriteBufferStr = "";
                
    }
   
    private void saveDataToFile(String LocalFileWriteBufferStr, String id) {
        /* write measurement data to the output file */
   	    try {
		    File root = Environment.getExternalStorageDirectory();
            if (root.canWrite()){
                File logfile = new File(root, filename);
                FileWriter logwriter = new FileWriter(logfile, true); /* true = append */
                BufferedWriter out = new BufferedWriter(logwriter);
                
                /* first, save debug info if activated */
                if (outputDebugInfo == true ) out.write(id);
                
                /* now save the data buffer into the file */
                out.write(LocalFileWriteBufferStr);
                out.close();
            }
        }    
        catch (IOException e) {
        /* don't do anything for the moment */
        }
        
    }
    
    
    /* The private PhoneState listener class that overrides the signal strength change method */
    /* This is where the main activity of the this app */
    private class MyPhoneStateListener extends PhoneStateListener {
  
      private static final int MAX_FILE_BUFFER_SIZE = 2000;

	  /* Get the Signal strength from the provider each time there is an update */
      @Override
      public void onSignalStrengthsChanged(SignalStrength signalStrength) {
    	 long NewCellId = 0; 
    	 long NewLacId = 0;
    	 
    	 String outputText; 
    	 
    	 /* a try enclosure is necessary as an exception is thrown inside if the network is currently
    	  * not available.
    	  */
    	 try {
    		 outputText = "Software Version: v20\r\n";
    		
    		 if (outputDebugInfo == true) outputText += "Debug Mode Activated\r\n";
    		 
    		 outputText += "\r\n";

    		 /* output signal strength value directly on canvas of the main activity */
             NumberOfSignalStrengthUpdates += 1;
             outputText += "Number of updates: " + String.valueOf(NumberOfSignalStrengthUpdates) + "\r\n\r\n";
                       
             outputText += "Network Operator: " + Tel.getNetworkOperator() + " "+ Tel.getNetworkOperatorName() + "\r\n";
             outputText += "Network Type: " + String.valueOf(Tel.getNetworkType()) + "\r\n\r\n";
             
             outputText = outputText + "Signal Strength: " + 
     		    String.valueOf(-113 + (2 * signalStrength.getGsmSignalStrength())) +  " dbm\r\n\r\n";
               
             GsmCellLocation myLocation = (GsmCellLocation) Tel.getCellLocation();
                  
             NewCellId = myLocation.getCid();  
             outputText += "Cell ID: " +  String.valueOf(NewCellId) + "\r\n";
                        
             NewLacId = myLocation.getLac();
             outputText += "LAC: " +  String.valueOf(NewLacId) + "\r\n\r\n";
                        
             /* Check if the current cell has changed and increase counter if necessary */
             if (NewCellId != LastCellId) {
            	 NumberOfCellChanges += 1; 
            	 LastCellId = NewCellId; 
             }
                          
             outputText += "Number of Cell Changes: " +  String.valueOf(NumberOfCellChanges) + "\r\n";
             
             /* Check if the current cell change is not a ping-pong cell change and increase counter */
        	 boolean IsCellInArray = false;

             for (int x = 0; x < PreviousCells.length; x++) {
            	 if (PreviousCells[x] == NewCellId){
            		 IsCellInArray = true;
            		 break;
            	 }
             }
             
             /* if the cell change was unique */
             if (IsCellInArray == false) {            	 
            	 /* increase unique cell change counter and save cell id in array at current index */
            	 NumberOfUniqueCellChanges++;
            	 PreviousCells [PreviousCellsIndex] = NewCellId;
         	         	 
            	 /* Increase index and wrap back to 0 in case it is at the end of the array */
            	 PreviousCellsIndex++;
            	 if (PreviousCellsIndex == PreviousCells.length)
            		 PreviousCellsIndex = 0;
             } /* else: do not increase the counter */
             
             outputText += "Number of Unique Cell Changes: " +  String.valueOf(NumberOfUniqueCellChanges) + "\r\n";

             
             /* Check if the current LAC has changed and increase counter if necessary */
             if (NewLacId != LastLacId) {
            	 NumberOfLacChanges += 1; 
            	 LastLacId = NewLacId; 
             }
             outputText += "Number of LAC Changes: " +  String.valueOf(NumberOfLacChanges) + "\r\n\r\n";
             
             /* Neighbor Cell Stuff */
             List<NeighboringCellInfo> nbcell = Tel.getNeighboringCellInfo ();
             outputText += "Number of Neighbors: "  + String.valueOf(nbcell.size()) + "\r\n";
             Iterator<NeighboringCellInfo> it = nbcell.iterator();
             while (it.hasNext()) {
            	 outputText += String.valueOf((it.next().getCid())) + "\r\n"; 
             }
             
             outputText += "\r\nOther signal info\r\n";
             outputText += "EcNo: " + String.valueOf(signalStrength.getCdmaEcio() +  "db\r\n");
             outputText += "WCDMA Signal: " + String.valueOf(signalStrength.getCdmaDbm() +  "dbm\r\n");
                     
             /* Write the information to a file, too 
              * This information is first buffered in a string buffer and only
              * written to the file once enough data has accumulated */ 
             FileWriteBufferStr += String.valueOf(NumberOfSignalStrengthUpdates) + ", ";
             FileWriteBufferStr += DateFormat.getDateInstance().format(new Date()) + ", ";
             FileWriteBufferStr += DateFormat.getTimeInstance().format(new Date()) + ", ";
             FileWriteBufferStr += String.valueOf(NewLacId) + ", ";
             FileWriteBufferStr += String.valueOf(NewCellId)+ ", ";
             FileWriteBufferStr += String.valueOf(-113 + (2 * signalStrength.getGsmSignalStrength()));
             FileWriteBufferStr += "\r\n";
             
             outputText += "File Buffer Length: " + FileWriteBufferStr.length() + "\r\n";
             
             if (FileWriteBufferStr.length() >= MAX_FILE_BUFFER_SIZE){
                 
            	 saveDataToFile(FileWriteBufferStr, "---in listener, " + DateFormat.getTimeInstance().format(new Date()) + "\r\n");
            	 FileWriteBufferStr = "";
             }
             
             super.onSignalStrengthsChanged(signalStrength);
             
    	 }
    	 catch (Exception e) {
    		 outputText = "No network information available..."; 
    	 }
 
         /* And finally, output the generated string with all the info retrieved to the screen */
         TextView tv = new TextView(getApplicationContext());
         tv.setText(outputText);
         setContentView(tv); 
      }

    };/* End of private Class */


}