package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kensuke1984.kibrary.util.FileUtils;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * A group of SAC files with the same network, station, location, channel, and qualityID.
 * This means that they are supposed to compose part of the same waveform.
 *
 * @author Kensuke Konishi
 */
class SegmentedSacGroup {

    /**
     * mergeする際のファイルのタイムウインドウのずれの許容範囲 sacfile のDELTA * maxgapNumber
     * 以上タイムウインドウに開きがあるとmergeしない
     */
    private static final int maxGapNumber = 500;

    /**
     * 作業フォルダ
     */
    private Path workPath;

    private Set<SacFileName> nameSet = new HashSet<>();

    /**
     * mergeしたSacFileName
     */
    private String mergedSacFileName;

    /**
     * 基準となる {@link SacFileName}
     */
    private SacFileName rootSacFileName;

    /**
     * 基本となる {@link SacFileName}を追加
     *
     * @param workPath    work path
     * @param sacFileName sacfile name
     */
    SegmentedSacGroup(Path workPath, SacFileName sacFileName) {
        this.workPath = workPath;
        nameSet.add(sacFileName);
        rootSacFileName = sacFileName;
        mergedSacFileName = sacFileName.getMergedFileName();
    }

    /**
     * Gets name of root SAC file of this group.
     *
     * @return (String) Name of root SAC file of this group.
     */
    String getRootSacFileName() {
        return rootSacFileName.toString();
    }

    /**
     * SacSetに{@link SacFileName}を加える 既に同じものが入っていたり
     * {@link SacFileName#isRelated(SacFileName)}がfalseの場合追加しない
     *
     * @param sacFileName to add
     * @return 追加したかどうか
     */
    boolean add(SacFileName sacFileName) {
        return rootSacFileName.isRelated(sacFileName) && nameSet.add(sacFileName);
    }

    /**
     * グループ内のSAC fileを trashに捨てる 存在していないと作成する
     */
    void move(Path trash) {
        nameSet.stream().map(Object::toString).map(workPath::resolve).forEach(srcPath -> {
            try {
                FileUtils.moveToDirectory(srcPath, trash, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Merges the series of related SAC files.
     * The SAC files will first be sorted based on {@link SacFileName#compareTo(SacFileName)}.
     * sortした後のファイルを一つ一つつなげていく だめな条件が出たファイルは関連するファイルも含めてゴミ箱行き
     * name1にname2をくっつける。 name1とname2が離れすぎていると そこでだめになる その一連のファイルもだめ
     *
     * @return うまくつなげられたかどうか
     */
    boolean merge() throws IOException {
        //System.out.println("merging");
        SacFileName[] sacFileNameList = nameSet.toArray(new SacFileName[0]);
        // sort the sacFileNameList
        // files will be sorted in order of starttime
        Arrays.sort(sacFileNameList);


        // <<<<まず基準となるSac(file0)を読み込む>>>>

        // read header and data of file0
        Path standardSacPath = workPath.resolve(sacFileNameList[0].toString());
        Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(standardSacPath);
        double[] standardSacData = SACUtil.readSACData(standardSacPath);

        // read delta of file0
        double delta = Double.parseDouble(headerMap.get(SACHeaderEnum.DELTA));
        long deltaInMillis = Math.round(1000 * delta);
        // calculate the largest acceptable gap. currentEndTimeとスタート時刻がmaxGap(msec) を超える波形はくっつけられない
        long maxGap = deltaInMillis * maxGapNumber;
        // half length of delta0 (msec)
        long halfDelta = deltaInMillis / 2;
        // read npts of file0
        int currentNpts = Integer.parseInt(headerMap.get(SACHeaderEnum.NPTS));

        // the output waveform
        List<Double> sacdata = new ArrayList<>(currentNpts);

        // timewindow length of file0 (msec)
        long timelength = deltaInMillis * (currentNpts - 1);
        // b value of file0 (msec)
        double currentB = Double.parseDouble(headerMap.get(SACHeaderEnum.B));
        long bInMillis = Math.round(currentB * 1000);
        //System.out.println(currentB*1000+" "+e0);
        // e value of file0 (msec)
        double e0 = Double.parseDouble(headerMap.get(SACHeaderEnum.E));
        long eInMillis = Math.round(e0 * 1000);
        //System.out.println(e0+" "+headerMap.get(SacHeaderEnum.E));
        //System.out.println("b, e "+bInMillis+", "+eInMillis);
        // current start time of waveform
        LocalDateTime currentStartTime = sacFileNameList[0].getStartTime().plus(bInMillis, ChronoUnit.MILLIS);
        // current end time of waveform startTime+ timelength0
        LocalDateTime currentEndTime = currentStartTime.plus(timelength, ChronoUnit.MILLIS);

        // add the waveform of file0 to the output waveform
        for (int j = 0; j < currentNpts; j++)
            sacdata.add(standardSacData[j]);


        // <<<<以下、file0につなげていくfile1, file2, file3, ... を読み込んでつなげていく>>>>

        if (sacFileNameList.length > 1) {
            System.err.println("++ merging : " + workPath.getFileName() + " - " + mergedSacFileName.toString());
        }
        for (int i = 1; i < sacFileNameList.length; i++) {
            // sacfilename to be joined (file1)
            SacFileName joinSacFileName = sacFileNameList[i];
            Path joinSacPath = workPath.resolve(joinSacFileName.toString());
            //System.err.println("joining " + joinSacFileName); // 4debug

            // つなげるsacfile(file1)の読み込み
            Map<SACHeaderEnum, String> headerMap1 = SACUtil.readHeader(joinSacPath);
            int npts = Integer.parseInt(headerMap1.get(SACHeaderEnum.NPTS));
            // double e = Double.parseDouble(headerMap1.get(SacHeaderEnum.E));
            double b = Double.parseDouble(headerMap1.get(SACHeaderEnum.B));
            long joinBInMillis = Math.round(b * 1000);
            // start time for joinSacfile
            LocalDateTime startTime = joinSacFileName.getStartTime().plus(joinBInMillis, ChronoUnit.MILLIS);
            // time length of joinsacfile (msec)
            timelength = deltaInMillis * (npts - 1);
            // end time of joinSacFile
            LocalDateTime endTime = startTime.plus(timelength, ChronoUnit.MILLIS);

            // 終了時刻がcurrentEndTimeより早いとmergeしない TODO 例外あり得るか？
            // 開始時刻はファイル名で並べられているはず
            if (endTime.isBefore(currentEndTime)) continue;

            // 直前のsac終了時刻から本sacの開始時刻までの時間差（ミリ秒）
            // 正なら重複部分なし 負なら重複時間あり
            long timeGap = currentEndTime.until(startTime, ChronoUnit.MILLIS);

            // 時間差がmaxGapより大きい場合NG TODO 将来的に0補完後捨てる？
            if (maxGap < timeGap) return false;

            // read data of file1
            //System.out.println("yes merging");
            double[] data = SACUtil.readSACData(joinSacPath);

            // 時間差が直前のファイルの終了時刻からDELTAの半分より後、それ以外で場合分け
            // 半分より後の場合はjoinsacの一番端をcurrentsacのとなりにくっつける
            // 半分より前の場合は調整してくっつける
            if (halfDelta < timeGap) {
                // joinsacがhalfDeltaより後に始まっている場合 そのままくっつける TODO 0埋めしなくていいの？ 500 msまでなら時間ずれちゃってもいいやってこと？
                for (int j = 0; j < npts; j++)
                    sacdata.add(data[j]);
                eInMillis += npts * deltaInMillis;
                currentNpts += npts;
            } else { // timeGap < halfDelta ; 重複あり
                // 小さい場合
                // int gap = (int) (1.5 * delta0 * 1000 - timeGap) / 1000;
                // joinsacのstart から currentsacの終了時刻のdelta後 までの時間差(msec)
                long gap = deltaInMillis - timeGap;
                // joinsacのstartから何msec目がcurrentsacの隣の値になるか
                int gapI = Math.round(gap / deltaInMillis);
                // 重複しない部分を追加していく
                for (int j = gapI; j < npts; j++)
                    sacdata.add(data[j]);
                // e0の更新
                eInMillis += (npts - gapI) * deltaInMillis;
                currentNpts += npts - gapI;
            }
            currentEndTime = endTime;
            currentStartTime = startTime;
        }
        //System.out.println(mergedSacFileName);

        if (sacdata.size() != currentNpts) {
            System.err.print("!! unexpected happened at merge, npts' are different ");
            System.err.println(sacdata.size() + " " + currentNpts + " : " + workPath.getFileName() + " - " + rootSacFileName.toString());
            return false;
        }

        //System.out.println(deltaInMillis+" "+bInMillis+" "+eInMillis);
        long timeDiff = (sacdata.size() - 1) * deltaInMillis + bInMillis - eInMillis;
        if (5 < timeDiff || timeDiff < -5) {
            //if ((sacdata.size()-1)*deltaInMillis+bInMillis != eInMillis) {
            System.err.print("!! unexpected happened at merge, currentE's are different ");
            System.err.println((sacdata.size() - 1) * deltaInMillis + bInMillis + " " + eInMillis
                    + " : " + workPath.getFileName() + " - " + rootSacFileName.toString());
            if (100 < timeDiff || timeDiff < -100) return false;
        }

        double e = eInMillis / 1000.0;
        //System.out.println(e+" "+eInMillis);
        headerMap.put(SACHeaderEnum.NPTS, String.valueOf(sacdata.size()));
        headerMap.put(SACHeaderEnum.E, String.valueOf(e));
        double[] sdata = sacdata.stream().mapToDouble(Double::doubleValue).toArray();

        SACUtil.writeSAC(workPath.resolve(mergedSacFileName), headerMap, sdata);
        return true;
    }

}
