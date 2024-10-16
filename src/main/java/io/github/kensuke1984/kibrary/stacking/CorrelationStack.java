package io.github.kensuke1984.kibrary.stacking;

import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 基準の波形のタイムウインドウ start &le; t &le; end をセットして その部分とのコリレーションがよくなるだけずらしてスタックする
 * 一度コリレーションを計算したものはそのまま戻す （同じ震源観測点の波形を再度代入すると同じだけずらす）
 * timewindowの長さは基準のタイムウインドウを十分に長くしないといけない。
 * <p>
 * TODO timewindowに複数はいっている場合に対処できていない timewindowの長さがちぐはぐでもいいようにする
 * 時間ステップの異なるデータに対してはできない
 *
 * @author kensuke
 * @version 0.1.1
 */
public class CorrelationStack implements Stack {

    // /**
    // * ずらす時間と±searchRegion秒考える 1秒刻み１０秒
    // */
    // private int searchRegion = 10;

    /**
     * 基準にするTrace. この部分に対して相関のいいように並べる
     */
    private Trace standardTrace;
    /**
     * ここに書かれたタイムウインドウで比較する
     */
    private Set<TimewindowData> timewindowInformationSet;
    /**
     * 既に計算したシフト。TODO 並列化に耐えられるようにしたい
     */
    private Map<Key, Integer> shiftMap = new HashMap<>();

    /**
     * traceのtimewindow部分をベースにする
     *
     * @param stationName              station name
     * @param id                       {@link GlobalCMTID}
     * @param component                {@link SACComponent}
     * @param type                     {@link WaveformType}
     * @param trace                    trace
     * @param window                   timewindow
     * @param timeWindowInformationSet Set of time window information
     */
    public CorrelationStack(String stationName, GlobalCMTID id, SACComponent component, WaveformType type, Trace trace,
                            Timewindow window, Set<TimewindowData> timeWindowInformationSet) {
        timewindowInformationSet = timeWindowInformationSet;
        double start = window.getStartTime();
        double end = window.getEndTime();
        double startOfTrace = trace.getX()[0];
        double endOfTrace = trace.getX()[trace.getX().length - 1];
        if (start < startOfTrace || endOfTrace < end) throw new RuntimeException(
                "input window [" + start + ", " + end + "] must be within the one of input trace [" + startOfTrace +
                        ", " + endOfTrace + "]");
        standardTrace = trace.cutWindow(window);
        shiftMap.put(new Key(stationName, id, component), 0);
    }

    /**
     * {@link #standardTrace}とベストな相関をとるためのshift量を計算する
     *
     * @param compareTrace
     * @return ずらす量 compareTraceをどれだけずらすか。 整数値であり時間は関係ない
     */
    private int search(Trace compareTrace) {
        // System.out.println(standardTrace);
        // double[] standardX = standardTrace.getX(); // 基準波形の時間
        double[] standardY = standardTrace.getY(); // 基準の波形
        // double[] compareX = compareTrace.getX(); // 合わせる波形の時間
        double[] compareY = compareTrace.getY(); // 合わせる波形
        // 基準波形の方が比較波形より短い場合 想定外
        if (standardY.length < compareY.length)
            throw new RuntimeException("Base timewindow must be bigger.");
        // 相関のベストなシフト 何ポイントずらせばよいか
        int bestShift = Trace.findBestShift(standardY, compareY);
        // System.out.println(corr);
        return bestShift;
    }

    @Override
    public Trace stack(String stationName, GlobalCMTID id, SACComponent component, WaveformType type, Trace trace) {
        if (timewindowInformationSet.stream().noneMatch(
                timewindow -> timewindow.getObserver().getStation().equals(stationName) &&
                        timewindow.getGlobalCMTID().equals(id) && timewindow.getComponent() == component)) {
            throw new RuntimeException("No timewindow information for " + stationName + " " + id + " " + component);
        }
        int shift;
        Key key = new Key(stationName, id, component);
        Timewindow window =
                timewindowInformationSet.stream().filter(info -> info.getObserver().getStation().equals(stationName))
                        .filter(info -> info.getGlobalCMTID().equals(id))
                        .filter(info -> info.getComponent() == component).findAny().get();
        System.out.println(stationName + " " + window.getStartTime() + " " + window.getEndTime());
        if (shiftMap.containsKey(key)) {
            shift = shiftMap.get(key);
        } else {
            // 相関をとりたいところの切り抜き
            Trace compareTrace = trace.cutWindow(window);
            shift = search(compareTrace);
            shiftMap.put(key, shift);
        }
        // 基準波形の相関をあわせる時刻(ここに比較波形部分のスタートを持ってこないといけない)
        double standartStarttime = standardTrace.getX()[shift];
        // 相関をあわせる比較波形部分のスタート時刻
        double compareStartTime = window.getStartTime();
        // これらの差
        double timeshift = standartStarttime - compareStartTime;

        double[] x = new double[trace.getLength()]; // 返す時間軸
        double[] y = new double[trace.getLength()]; // 返す波形
        for (int i = 0; i < trace.getLength(); i++) {
            x[i] = trace.getX()[i] + timeshift; // 基準の時間窓のコピーに先ほどの差を考慮
            y[i] = trace.getY()[i]; // コピー
        }
        return new Trace(x, y);
    }

    private class Key {
        private String stationName;
        private GlobalCMTID id;
        private SACComponent component;

        private Key(String stationName, GlobalCMTID id, SACComponent component) {
            this.stationName = stationName;
            this.id = id;
            this.component = component;
        }

        @Override
        public int hashCode() {
            int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((component == null) ? 0 : component.hashCode());
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((stationName == null) ? 0 : stationName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Key other = (Key) obj;
            if (!getOuterType().equals(other.getOuterType())) return false;
            if (component != other.component) return false;
            if (id == null) {
                if (other.id != null) return false;
            } else if (!id.equals(other.id)) return false;
            if (stationName == null) {
                if (other.stationName != null) return false;
            } else if (!stationName.equals(other.stationName)) return false;
            return true;
        }

        private CorrelationStack getOuterType() {
            return CorrelationStack.this;
        }

    }
}
