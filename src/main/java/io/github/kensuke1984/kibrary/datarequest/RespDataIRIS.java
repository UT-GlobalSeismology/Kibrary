package io.github.kensuke1984.kibrary.datarequest;

import io.github.kensuke1984.kibrary.util.Utilities;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;


/**
 * Class for RESP files, which allows us to download RESP files IRIS DMC IRISWS RESP Web Service
 * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web Service Documentation
 * @author Kenji Kawai
 * @version 0.1.1
 */
public class RespDataIRIS {

	private String url = "http://service.iris.edu/irisws/resp/1/query";
	private String responseFile = "RESP.";

	/**
	 * Constructor with options for IRIS DMC IRISWS RESP Web Service
	 * 
	 * @see <a href=http://service.iris.edu/irisws/resp/1/> IRIS DMC IRISWS RESP Web
	 *      Service Documentation
	 * @param network  (String) Regular network (ex. IU) or virtual network (ex. _FDSN).
	 * @param station  (String) Station code.
	 * @param location (String) Location code. Use "--" instead of spaces.
	 * @param channel  (String) Channel code.
	 * @param time     (Calendar) Find the response for the given time.
	 */
	public RespDataIRIS(String network, String station, String location, String channel, Calendar time) {

		String yyyy = Utilities.formatNumber(time.get(Calendar.YEAR), 3000);
		String MM = Utilities.formatNumber(time.get(Calendar.MONTH) + 1, 12);
		String dd = Utilities.formatNumber(time.get(Calendar.DATE), 31);
		String HH = Utilities.formatNumber(time.get(Calendar.HOUR_OF_DAY), 24);
		String mm = Utilities.formatNumber(time.get(Calendar.MINUTE), 60);
		String ss = Utilities.formatNumber(time.get(Calendar.SECOND), 60);

		url = "http://service.iris.edu/irisws/resp/1/query?" + "net=" + network + "&" + "sta=" + station + "&" + "cha="
				+ channel + "&" + "loc=" + location + "&" + "time=" + yyyy + "-" + MM + "-" + dd + "T" + HH + ":" + mm
				+ ":" + ss;

		responseFile = "RESP." + network + "." + station + "." + location + "." + channel;

//		System.out.println(responseFile);
//		System.out.println(url);
	}

	public void downloadResp() {
		Path outPath = Paths.get(responseFile); // TODO　出力のディレクトリの指定
		
		try {
			URL IRISWSURL = new URL(url);
			long size = 0L;

			size = Files.copy(IRISWSURL.openStream(), outPath , StandardCopyOption.REPLACE_EXISTING); // overwriting
			System.out.println("Downloaded : " + responseFile + " - " + size + " bytes");

		} catch (IOException e) {
			System.out.println(e);
		}

	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		Calendar time = Calendar.getInstance();
		time.set(2000, 0, 1);

		RespDataIRIS rd = new RespDataIRIS("II", "PFO", "00", "BHE", time);
		rd.downloadResp();

	}

}
