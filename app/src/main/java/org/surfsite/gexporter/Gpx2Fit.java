package org.surfsite.gexporter;

import com.garmin.fit.CourseMesg;
import com.garmin.fit.CoursePoint;
import com.garmin.fit.CoursePointMesg;
import com.garmin.fit.DateTime;
import com.garmin.fit.Event;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventType;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Fit;
import com.garmin.fit.LapMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.RecordMesg;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Gpx2Fit {
    private static final String HTTP_WWW_TOPOGRAFIX_COM_GPX_1_0 = "http://www.topografix.com/GPX/1/0";
    private static final String HTTP_WWW_TOPOGRAFIX_COM_GPX_1_1 = "http://www.topografix.com/GPX/1/1";

    private final List<WayPoint> wayPoints = new ArrayList<>();
    private String courseName;
    private String ns = HTTP_WWW_TOPOGRAFIX_COM_GPX_1_0;
    GpxToFitOptions mGpxToFitOptions;

    public Gpx2Fit(File file, GpxToFitOptions options) throws Exception {
        mGpxToFitOptions = options;
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        //parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        courseName = file.getName();
        if (courseName.endsWith(".fit") || courseName.endsWith(".FIT")
                || courseName.endsWith(".gpx") || courseName.endsWith(".GPX")  ) {
            courseName = courseName.substring(0, courseName.length()-4);
        }

        if (courseName.length() > 15) {
            courseName = courseName.substring(0, 15);
        }

        FileInputStream in = new FileInputStream(file);

        try {
            parser.setInput(in, null);
            parser.nextTag();
            readGPX(parser);
            in.close();
            return;
        } catch (Exception e) {
            ns = HTTP_WWW_TOPOGRAFIX_COM_GPX_1_1;
        } finally {
            in.close();
        }

        in = new FileInputStream(file);

        try {
            parser.setInput(in, null);
            parser.nextTag();
            readGPX(parser);
        } finally {
            in.close();
        }
    }

    public List<WayPoint> getWaypoints() {
        return wayPoints;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    private void readGPX(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "gpx");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            switch (name) {
                case "trk":
                    readTrk(parser);
                    // If waypoints found, bail out.
                    if (wayPoints.size() > 0)
                        return;
                    break;
                case "rte":
                    readRte(parser);
                    // If waypoints found, bail out.
                    if (wayPoints.size() > 0)
                        return;
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
    }

    private void readTrk(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trk");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            switch (name) {
                case "trkseg":
                    readTrkSeg(parser);
                    break;
/*
                case "name":
                    courseName = readTrkName(parser);
                    break;
*/
                default:
                    skip(parser);
                    break;
            }
        }
    }

    private String readTrkName(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "name");
        String txt = readText(parser);
        if (txt.length() > 15)
            txt = txt.substring(0, 15);
        parser.require(XmlPullParser.END_TAG, ns, "name");
        return txt;
    }

    private void readTrkSeg(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trkseg");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("trkpt")) {
                readTrkPt(parser);
            } else {
                skip(parser);
            }
        }
    }

    private void readRte(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "rte");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            switch (name) {
                case "rtept":
                    readRtePt(parser);
                    break;
/*
                case "name":
                    courseName = readTrkName(parser);
                    break;
*/
                default:
                    skip(parser);
                    break;
            }
        }
    }

    private void readTrkPt(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "trkpt");
        Date time = null;
        double lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
        double ele = Double.NaN;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            switch (name) {
                case "ele":
                    ele = readEle(parser);
                    break;
                case "time":
                    time = readTime(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        wayPoints.add(new WayPoint(lat, lon, ele, time));
    }

    private void readRtePt(XmlPullParser parser) throws XmlPullParserException, IOException, ParseException {
        parser.require(XmlPullParser.START_TAG, ns, "rtept");
        Date time = null;
        double lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
        double lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
        double ele = Double.NaN;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            switch (name) {
                case "ele":
                    ele = readEle(parser);
                    break;
                case "time":
                    time = readTime(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        wayPoints.add(new WayPoint(lat, lon, ele, time));
    }

    private double readEle(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "ele");
        String txt = readText(parser);
        double ele = Double.parseDouble(txt);
        parser.require(XmlPullParser.END_TAG, ns, "ele");
        return ele;
    }

    private Date readTime(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "time");
        String txt = readText(parser);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date time;
        try {
            time = dateFormat.parse(txt);
        } catch (ParseException e) {
            time = null;
        } finally {
            parser.require(XmlPullParser.END_TAG, ns, "time");
        }
        return time;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    public String getName() {
        return courseName;
    }

    /**
     * Grade adjusted pace based on a study by Alberto E. Minetti on the energy cost of
     * walking and running at extreme slopes.
     *
     * see Minetti, A. E. et al. (2002). Energy cost of walking and running at extreme uphill and downhill slopes.
     *      Journal of Applied Physiology 93, 1039-1046, http://jap.physiology.org/content/93/3/1039.full
     */
    public double getWalkingGradeFactor(double g)
    {
        return 1.0 + (g * (19.5 + g * (46.3 + g * (-43.3 + g * (-30.4 + g * 155.4))))) / 3.6;
    }

    public void writeFit(File outfile) {
        WayPoint last = null;
        double minEle = Double.NaN;
        double maxEle = Double.NaN;
        double totalAsc = Double.NaN;
        double totalDesc = Double.NaN;
        double dist = .0;
        double totaldist = .0;
        double cp_min_dist = .0;
        double lcdist = .0;
        double ldist = .0;
        double speed = mGpxToFitOptions.getSpeed();

        FileEncoder encode = new FileEncoder(outfile, Fit.ProtocolVersion.V2_0);

        //Generate FileIdMessage
        FileIdMesg fileIdMesg = new FileIdMesg(); // Every FIT file MUST contain a 'File ID' message as the first message
        fileIdMesg.setManufacturer(Manufacturer.DYNASTREAM);
        fileIdMesg.setType(com.garmin.fit.File.COURSE);
        fileIdMesg.setProduct(12345);
        fileIdMesg.setSerialNumber(12345L);
        fileIdMesg.setNumber(wayPoints.hashCode());
        fileIdMesg.setTimeCreated(new DateTime(new Date()));
        encode.write(fileIdMesg); // Encode the FileIDMesg

        CourseMesg courseMesg = new CourseMesg();
        courseMesg.setLocalNum(1);
        courseMesg.setName(getName());
        encode.write(courseMesg);

        LapMesg lapMesg = new LapMesg();
        lapMesg.setLocalNum(2);
        EventMesg eventMesg = new EventMesg();
        eventMesg.setLocalNum(3);
        RecordMesg r = new RecordMesg();
        r.setLocalNum(4);
        CoursePointMesg cp = new CoursePointMesg();
        cp.setLocalNum(5);

        System.out.println(String.format("Track: %s", getName()));
        WayPoint firstWayPoint = wayPoints.get(0);
        Date startDate = firstWayPoint.getTime();

        WayPoint lastWayPoint = wayPoints.get(wayPoints.size() - 1);

        boolean forceSpeed = mGpxToFitOptions.isForceSpeed();
        if (firstWayPoint.getTime().getTime() == lastWayPoint.getTime().getTime()) {
            if (!Double.isNaN(speed))
                forceSpeed = true;
        }
        Date endDate;

        if (forceSpeed) {
            endDate = startDate;
        } else {
            endDate = lastWayPoint.getTime();
        }

        for (WayPoint wpt : wayPoints) {
            double ele = wpt.getEle();
            if (!Double.isNaN(ele)) {
                if (minEle > ele || Double.isNaN(minEle))
                    minEle = ele;
                if (maxEle < ele || Double.isNaN(maxEle))
                    maxEle = ele;
            }

            double grade = .0;
            double gspeed = speed;
            if (last == null) {
                wpt.setTotaldist(.0);
            } else {
                double d = wpt.distance(last);
                double gradedist = d;

                if (mGpxToFitOptions.isUse3dDistance()) {
                    totaldist += wpt.distance3D(last);
                } else {
                    totaldist += d;
                }

                wpt.setTotaldist(totaldist);

                if (! Double.isNaN(ele) && ! Double.isNaN(last.getEle())) {
                    double dele = ele - last.getEle();
                    if (dele > 0.0)
                        totalAsc += dele;
                    else
                        totalDesc += Math.abs(dele);

                    if (mGpxToFitOptions.isWalkingGrade()) {
                        grade = dele / gradedist;
                        gspeed = getWalkingGradeFactor(grade) * speed;
                    }
                }

                if (forceSpeed) {
                    endDate = new Date(endDate.getTime() + (long) (d / gspeed * 1000.0));
                    wpt.setTime(endDate);
                }
            }
            last = wpt;
            System.out.println(String.format("%s [%s , %s] %s - %s", endDate, wpt.getLat(), wpt.getLon(), ele, wpt.getTotaldist()));
        }

        lapMesg.setTotalDistance((float) totaldist);

        if (!Double.isNaN(totalAsc)) {
            totalAsc += 0.5;
            lapMesg.setTotalAscent((int) totalAsc);
        }
        if (!Double.isNaN(totalDesc)) {
            totalDesc += 0.5;
            lapMesg.setTotalDescent((int) totalDesc);
        }
        if (!Double.isNaN(maxEle))
            lapMesg.setMaxAltitude((float) maxEle);

        if (!Double.isNaN(minEle))
            lapMesg.setMinAltitude((float) minEle);

        lapMesg.setStartPositionLat(firstWayPoint.getLatSemi());
        lapMesg.setStartPositionLong(firstWayPoint.getLonSemi());
        lapMesg.setStartTime(new DateTime(startDate));

        lapMesg.setEndPositionLat(lastWayPoint.getLatSemi());
        lapMesg.setEndPositionLong(lastWayPoint.getLonSemi());

        System.out.println(String.format("Start: %s - End: %s", startDate.toString(), endDate.toString()));

        long duration = endDate.getTime() - startDate.getTime();

        lapMesg.setTotalElapsedTime((float) (duration / 1000.0));
        lapMesg.setTotalTimerTime((float) (duration / 1000.0));
        lapMesg.setAvgSpeed((float) (totaldist * 1000.0 / (double) duration));

        encode.write(lapMesg);

        cp_min_dist = totaldist / 48.0;
        if (cp_min_dist < mGpxToFitOptions.getMinCoursePointDistance())
            cp_min_dist = mGpxToFitOptions.getMinCoursePointDistance();

        double pt_min_dist = 0;
        if (mGpxToFitOptions.getMaxPoints() != 0)
            pt_min_dist = totaldist / mGpxToFitOptions.getMaxPoints();
        if (pt_min_dist < mGpxToFitOptions.getMinRoutePointDistance())
            pt_min_dist = mGpxToFitOptions.getMinRoutePointDistance();

        eventMesg.setEvent(Event.TIMER);
        eventMesg.setEventType(EventType.START);
        eventMesg.setEventGroup((short) 0);
        eventMesg.setTimestamp(new DateTime(startDate));
        encode.write(eventMesg);
        last = null;

        DateTime timestamp = new DateTime(new Date(WayPoint.RefMilliSec));
        long ltimestamp = startDate.getTime();

        long i = 0;

        for (WayPoint wpt : wayPoints) {
            boolean written = false;
            i += 1;

            if (duration != 0)
                timestamp = new DateTime(wpt.getTime());
            else
                timestamp = new DateTime(new Date(WayPoint.RefMilliSec + i * 1000));

            double gspeed = Double.NaN;
            dist = wpt.getTotaldist();

            if (last == null) {
                cp.setPositionLat(wpt.getLatSemi());
                cp.setPositionLong(wpt.getLonSemi());
                cp.setName("Start");
                cp.setType(CoursePoint.GENERIC);
                cp.setDistance((float) dist);
                cp.setTimestamp(timestamp);
                encode.write(cp);
                written = true;
            }

            if ((last == null) || (dist - ldist) > pt_min_dist) {
                r.setPositionLat(wpt.getLatSemi());
                r.setPositionLong(wpt.getLonSemi());
                r.setDistance((float) dist);
                r.setTimestamp(timestamp);

                if (!Double.isNaN(wpt.getEle()))
                    r.setAltitude((float) wpt.getEle());

                long l = timestamp.getDate().getTime();

                if (ltimestamp != l) {
                    gspeed = (dist - ldist) / (l - ltimestamp) * 1000.0;
                    r.setSpeed((float) gspeed);
                }

                encode.write(r);
                ldist = dist;
                ltimestamp = l;
                written = true;
            }

            if (wpt == lastWayPoint) {
                cp.setPositionLat(wpt.getLatSemi());
                cp.setPositionLong(wpt.getLonSemi());
                cp.setName("End");
                cp.setType(CoursePoint.GENERIC);
                cp.setDistance((float) dist);
                cp.setTimestamp(timestamp);
                encode.write(cp);
                written = true;
            } else if (mGpxToFitOptions.isInjectCoursePoints() && ((dist - lcdist) > cp_min_dist)) {
                cp.setName("");
                cp.setType(CoursePoint.GENERIC);
                cp.setPositionLat(wpt.getLatSemi());
                cp.setPositionLong(wpt.getLonSemi());
                cp.setDistance((float) dist);
                cp.setTimestamp(timestamp);
                encode.write(cp);
                lcdist = dist;
                written = true;
            }

            last = wpt;
            if (written) {
                System.out.println(String.format("%s [%s , %s] %s - %f - %f", timestamp.toString(),
                        wpt.getLat(), wpt.getLon(), wpt.getEle(), dist, gspeed));
            }
        }

        eventMesg.setEvent(Event.TIMER);
        eventMesg.setEventType(EventType.STOP_DISABLE_ALL);
        eventMesg.setEventGroup((short) 0);
        //timestamp.add(2);
        eventMesg.setTimestamp(timestamp);

        encode.write(eventMesg);
        encode.close();
    }
}
