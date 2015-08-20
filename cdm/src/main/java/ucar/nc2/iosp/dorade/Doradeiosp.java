/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.nc2.iosp.dorade;

import ucar.ma2.*;
import ucar.nc2.*;
import ucar.nc2.iosp.AbstractIOServiceProvider;
import ucar.atd.dorade.*;


import java.io.*;
import java.util.*;

/**
 * IOServiceProvider implementation abstract base class to read/write Dorade files
 */

public class Doradeiosp extends AbstractIOServiceProvider {

  protected Doradeheader headerParser;

  public DoradeSweep mySweep = null;

  public boolean isValidFile(ucar.unidata.io.RandomAccessFile raf) {
    return Doradeheader.isValidFile(raf);
  }

  public String getFileTypeId() {
    return "DORADE";
  }

  public String getFileTypeDescription() {
    return "DOppler RAdar Data Exchange Format";
  }

  /////////////////////////////////////////////////////////////////////////////
  // reading

  public void open(ucar.unidata.io.RandomAccessFile raf, ucar.nc2.NetcdfFile ncfile,
                   ucar.nc2.util.CancelTask cancelTask) throws IOException {

    super.open(raf, ncfile, cancelTask);

    mySweep = new DoradeSweep(raf.getRandomAccessFile());
    headerParser = new Doradeheader();
    headerParser.read(mySweep, ncfile, null);

    ncfile.finish();
  }


  public Array readData(ucar.nc2.Variable v2, Section section) throws IOException, InvalidRangeException {

    Array outputData;
    int nSensor = mySweep.getNSensors();
    int nRays = mySweep.getNRays();

    if (v2.getShortName().equals("elevation")) {
      float[] elev = mySweep.getElevations();
      outputData = readData1(v2, section, elev);
      //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), elev);
    } else if (v2.getShortName().equals("rays_time")) {
      Date[] dd = mySweep.getTimes();
      if (dd == null)
        throw new IllegalStateException("missing dates for "+v2.getShortName());
      double[] d = new double[dd.length];
      for (int i = 0; i < dd.length; i++)
        d[i] = (double) dd[i].getTime();
      outputData = readData2(v2, section, d);
      //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), d);
    } else if (v2.getShortName().equals("azimuth")) {
      float[] azim = mySweep.getAzimuths();
      outputData = readData1(v2, section, azim);
      //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), azim);
    } else if (v2.getShortName().startsWith("latitudes_")) {
      float[] allLats = new float[nSensor * nRays];
      float[] lats;
      for (int i = 0; i < nSensor; i++) {
        lats = mySweep.getLatitudes(i);
        System.arraycopy(lats, 0, allLats, i * nRays, nRays);
      }
      outputData = readData1(v2, section, allLats);
      //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), allLats);
    } else if (v2.getShortName().startsWith("longitudes_")) {
      float[] allLons = new float[nSensor * nRays];
      float[] lons;
      for (int i = 0; i < nSensor; i++) {
        lons = mySweep.getLongitudes(i);
        System.arraycopy(lons, 0, allLons, i * nRays, nRays);
      }
      outputData = readData1(v2, section, allLons);
      //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), lons);
    } else if (v2.getShortName().startsWith("altitudes_")) {
      float[] allAlts = new float[nSensor * nRays];
      float[] alts;
      for (int i = 0; i < nSensor; i++) {
        alts = mySweep.getAltitudes(i);
        System.arraycopy(alts, 0, allAlts, i * nRays, nRays);
      }
      outputData = readData1(v2, section, allAlts);
      //outputData = Array.factory( v2.getDataType().getPrimitiveClassType(), v2.getShape(), alts);
    } else if (v2.getShortName().startsWith("distance_")) {
      float[] dist;
      int j = 0;
      for (int i = 0; i < nSensor; i++) {
        String t = "" + i;
        if (v2.getShortName().endsWith(t)) {
          j = i;
          break;
        }
      }
      int nc = mySweep.getNCells(j);
      Array data = Array.makeArray(DataType.FLOAT, nc,
          (double) mySweep.getRangeToFirstCell(j), (double) mySweep.getCellSpacing(j));
      dist = (float[]) data.get1DJavaArray(Float.class);
      outputData = readData1(v2, section, dist);

    } else if (v2.isScalar()) {

      float d = 0.0f;

      if (v2.getShortName().equals("Range_to_First_Cell")) {
        d = mySweep.getRangeToFirstCell(0);
      } else if (v2.getShortName().equals("Cell_Spacing")) {
        d = mySweep.getCellSpacing(0);
      } else if (v2.getShortName().equals("Fixed_Angle")) {
        d = mySweep.getFixedAngle();
      } else if (v2.getShortName().equals("Nyquist_Velocity")) {
        d = mySweep.getUnambiguousVelocity(0);
      } else if (v2.getShortName().equals("Unambiguous_Range")) {
        d = mySweep.getunambiguousRange(0);
      } else if (v2.getShortName().equals("Radar_Constant")) {
        d = mySweep.getradarConstant(0);
      } else if (v2.getShortName().equals("rcvr_gain")) {
        d = mySweep.getrcvrGain(0);
      } else if (v2.getShortName().equals("ant_gain")) {
        d = mySweep.getantennaGain(0);
      } else if (v2.getShortName().equals("sys_gain")) {
        d = mySweep.getsystemGain(0);
      } else if (v2.getShortName().equals("bm_width")) {
        d = mySweep.gethBeamWidth(0);
      }
      float[] dd = new float[1];
      dd[0] = d;
      outputData = Array.factory(v2.getDataType(), v2.getShape(), dd);
    } else {
      Range radialRange = section.getRange(0);
      Range gateRange = section.getRange(1);

      Array data = Array.factory(v2.getDataType(), section.getShape());
      IndexIterator ii = data.getIndexIterator();

      DoradePARM dp = mySweep.lookupParamIgnoreCase(v2.getShortName());
      if (dp == null)
        throw new IllegalStateException("Cant find param "+v2.getShortName());
      int ncells = dp.getNCells();
      float[] rayValues = new float[ncells];
      /*
  float[] allValues = new float[nRays * ncells];
  for (int r = 0; r < nRays; r++) {
      try {
          rayValues = mySweep.getRayData(dp, r, rayValues);
      } catch (DoradeSweep.DoradeSweepException ex) {
           ex.printStackTrace();
      }
      System.arraycopy(rayValues, 0, allValues, r*ncells, ncells);
  }    */
      for (int radialIdx : radialRange) {
        try {
          rayValues = mySweep.getRayData(dp, radialIdx, rayValues);
        } catch (DoradeSweep.DoradeSweepException ex) {
          ex.printStackTrace();
        }
        for (int gateIdx : gateRange) {
          ii.setFloatNext(rayValues[gateIdx]);
        }

      }
      return data;
      //outputData = Array.factory( v2.getDataType(), v2.getShape(), allValues);
    }

    return outputData;
  }

  public Array readData1(Variable v2, Section section, float[] values) {
    Array data = Array.factory(v2.getDataType(), section.getShape());
    IndexIterator ii = data.getIndexIterator();
    Range radialRange = section.getRange(0);     // radial range can also be gate range

    for (int radialIdx : radialRange) {
      ii.setFloatNext(values[radialIdx]);
    }

    return data;
  }

  public Array readData2(Variable v2, Section section, double[] values) {
    Array data = Array.factory(v2.getDataType(), section.getShape());
    IndexIterator ii = data.getIndexIterator();
    Range radialRange = section.getRange(0);

    for (int radialIdx : radialRange) {
      ii.setDoubleNext(values[radialIdx]);
    }

    return data;
  }

}
