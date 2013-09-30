/*
 * Copyright 2007 Daniel Spiewak.
 * Copyright 2013 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.charting;

import java.awt.Color;
import java.awt.Dimension;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebComponent;
import org.wicketstuff.googlecharts.ChartDataEncoding;
import org.wicketstuff.googlecharts.IChartAxis;
import org.wicketstuff.googlecharts.IChartData;
import org.wicketstuff.googlecharts.IChartFill;
import org.wicketstuff.googlecharts.IChartGrid;
import org.wicketstuff.googlecharts.IChartProvider;
import org.wicketstuff.googlecharts.IFillArea;
import org.wicketstuff.googlecharts.ILineStyle;
import org.wicketstuff.googlecharts.ILinearGradientFill;
import org.wicketstuff.googlecharts.ILinearStripesFill;
import org.wicketstuff.googlecharts.IRangeMarker;
import org.wicketstuff.googlecharts.IShapeMarker;
import org.wicketstuff.googlecharts.ISolidFill;
import org.wicketstuff.googlecharts.Range;

/**
 * This is a fork of org.wicketstuff.googlecharts.Chart whose only purpose
 * is to build https urls instead of http urls.
 *
 * @author Daniel Spiewak
 * @author James Moger
 */
public class SecureChart extends WebComponent implements Serializable {

    private static final long serialVersionUID = 6286305912682861488L;
    private IChartProvider provider;
    private StringBuilder url;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public SecureChart(String id, IChartProvider provider) {
        super(id);

        this.provider = provider;
    }

    public void invalidate() {
        lock.writeLock().lock();
        try {
            url = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CharSequence constructURL() {
        lock.writeLock().lock();
        try {
            if (url != null) {
                return url;
            }

            url = new StringBuilder("https://chart.googleapis.com/chart?");

            addParameter(url, "chs", render(provider.getSize()));
            addParameter(url, "chd", render(provider.getData()));
            addParameter(url, "cht", render(provider.getType()));
            addParameter(url, "chbh", render(provider.getBarWidth(), provider.getBarGroupSpacing()));
            addParameter(url, "chtt", render(provider.getTitle()));
            addParameter(url, "chdl", render(provider.getLegend()));
            addParameter(url, "chco", render(provider.getColors()));

            IChartFill bgFill = provider.getBackgroundFill();
            IChartFill fgFill = provider.getChartFill();

            StringBuilder fillParam = new StringBuilder();

            if (bgFill != null) {
                fillParam.append("bg,").append(render(bgFill));
            }

            if (fgFill != null) {
                if (fillParam.length() > 0) {
                    fillParam.append('|');
                }

                fillParam.append("c,").append(render(fgFill));
            }

            if (fillParam.toString().trim().equals("")) {
                fillParam = null;
            }

            addParameter(url, "chf", fillParam);

            IChartAxis[] axes = provider.getAxes();
            addParameter(url, "chxt", renderTypes(axes));
            addParameter(url, "chxl", renderLabels(axes));
            addParameter(url, "chxp", renderPositions(axes));
            addParameter(url, "chxr", renderRanges(axes));
            addParameter(url, "chxs", renderStyles(axes));

            addParameter(url, "chg", render(provider.getGrid()));
            addParameter(url, "chm", render(provider.getShapeMarkers()));
            addParameter(url, "chm", render(provider.getRangeMarkers()));
            addParameter(url, "chls", render(provider.getLineStyles()));
            addParameter(url, "chm", render(provider.getFillAreas()));
            addParameter(url, "chl", render(provider.getPieLabels()));

            return url;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addParameter(StringBuilder url, CharSequence param, CharSequence value) {
        if (value == null || value.length() == 0) {
            return;
        }

        if (url.charAt(url.length() - 1) != '?') {
            url.append('&');
        }

        url.append(param).append('=').append(value);
    }

    private CharSequence convert(ChartDataEncoding encoding, double value, double max) {
    	switch (encoding) {
    	case TEXT:
    		return SecureChartDataEncoding.TEXT.convert(value, max);
    	case EXTENDED:
    		return SecureChartDataEncoding.EXTENDED.convert(value, max);
    	case SIMPLE:
    	default:
    		return SecureChartDataEncoding.SIMPLE.convert(value, max);
    	}
    }

    private CharSequence render(Dimension dim) {
        if (dim == null) {
            return null;
        }

        return new StringBuilder().append(dim.width).append('x').append(dim.height);
    }

    private CharSequence render(IChartData data) {
        if (data == null) {
            return null;
        }

        ChartDataEncoding encoding = data.getEncoding();

        StringBuilder back = new StringBuilder();
        back.append(render(encoding)).append(':');

        for (double[] set : data.getData()) {
            if (set == null || set.length == 0) {
                back.append(convert(encoding, -1, data.getMax()));
            } else {
                for (double value : set) {
                    back.append(convert(encoding, value, data.getMax())).append(encoding.getValueSeparator());
                }

                if (back.substring(back.length() - encoding.getValueSeparator().length(),
                        back.length()).equals(encoding.getValueSeparator())) {
                    back.setLength(back.length() - encoding.getValueSeparator().length());
                }
            }

            back.append(encoding.getSetSeparator());
        }

        if (back.substring(back.length() - encoding.getSetSeparator().length(),
                back.length()).equals(encoding.getSetSeparator())) {
            back.setLength(back.length() - encoding.getSetSeparator().length());
        }

        return back;
    }

    private CharSequence render(Enum<?> value) {
        if (value == null) {
            return null;
        }

        try {
            Object back = value.getClass().getMethod("getRendering").invoke(value);

            if (back != null) {
                return back.toString();
            }
        } catch (IllegalArgumentException e) {
        } catch (SecurityException e) {
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        } catch (NoSuchMethodException e) {
        }

        return null;
    }

    private CharSequence render(int barWidth, int groupSpacing) {
        if (barWidth == -1) {
            return null;
        }

        StringBuilder back = new StringBuilder(barWidth);

        if (groupSpacing >= 0) {
            back.append(',').append(groupSpacing);
        }

        return back;
    }

    private CharSequence render(String[] values) {
        if (values == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (String value : values) {
            CharSequence toRender = render(value);
            if (toRender == null) {
                toRender = "";
            }

            back.append(toRender).append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence render(String value) {
        if (value == null) {
            return value;
        }

        StringBuilder back = new StringBuilder();

        for (char c : value.toCharArray()) {
            if (c == ' ') {
                back.append('+');
            } else {
                back.append(c);
            }
        }

        return back;
    }

    private CharSequence render(Color[] values) {
        if (values == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (Color value : values) {
            CharSequence toRender = render(value);
            if (toRender == null) {
                toRender = "";
            }

            back.append(toRender).append(',');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence render(Color value) {
        if (value == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        {
            String toPad = Integer.toHexString(value.getRed());

            if (toPad.length() == 1) {
                back.append(0);
            }
            back.append(toPad);
        }

        {
            String toPad = Integer.toHexString(value.getGreen());

            if (toPad.length() == 1) {
                back.append(0);
            }
            back.append(toPad);
        }

        {
            String toPad = Integer.toHexString(value.getBlue());

            if (toPad.length() == 1) {
                back.append(0);
            }
            back.append(toPad);
        }

        {
            String toPad = Integer.toHexString(value.getAlpha());

            if (toPad.length() == 1) {
                back.append(0);
            }
            back.append(toPad);
        }

        return back;
    }

    private CharSequence render(IChartFill fill) {
        if (fill == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        if (fill instanceof ISolidFill) {
            ISolidFill solidFill = (ISolidFill) fill;

            back.append("s,");
            back.append(render(solidFill.getColor()));
        } else if (fill instanceof ILinearGradientFill) {
            ILinearGradientFill gradientFill = (ILinearGradientFill) fill;

            back.append("lg,").append(gradientFill.getAngle()).append(',');

            Color[] colors = gradientFill.getColors();
            double[] offsets = gradientFill.getOffsets();
            for (int i = 0; i < colors.length; i++) {
                back.append(render(colors[i])).append(',').append(offsets[i]).append(',');
            }

            back.setLength(back.length() - 1);
        } else if (fill instanceof ILinearStripesFill) {
            ILinearStripesFill stripesFill = (ILinearStripesFill) fill;

            back.append("ls,").append(stripesFill.getAngle()).append(',');

            Color[] colors = stripesFill.getColors();
            double[] widths = stripesFill.getWidths();
            for (int i = 0; i < colors.length; i++) {
                back.append(render(colors[i])).append(',').append(widths[i]).append(',');
            }

            back.setLength(back.length() - 1);
        } else {
            return null;
        }

        return back;
    }

    private CharSequence renderTypes(IChartAxis[] axes) {
        if (axes == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (IChartAxis axis : axes) {
            back.append(render(axis.getType())).append(',');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence renderLabels(IChartAxis[] axes) {
        if (axes == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (int i = 0; i < axes.length; i++) {
            if (axes[i] == null || axes[i].getLabels() == null) {
                continue;
            }

            back.append(i).append(":|");

            for (String label : axes[i].getLabels()) {
                if (label == null) {
                    back.append('|');
                    continue;
                }

                back.append(render(label)).append('|');
            }

            if (i == axes.length - 1) {
                back.setLength(back.length() - 1);
            }
        }

        return back;
    }

    private CharSequence renderPositions(IChartAxis[] axes) {
        if (axes == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (int i = 0; i < axes.length; i++) {
            if (axes[i] == null || axes[i].getPositions() == null) {
                continue;
            }

            back.append(i).append(',');

            for (double position : axes[i].getPositions()) {
                back.append(position).append(',');
            }

            back.setLength(back.length() - 1);

            back.append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence renderRanges(IChartAxis[] axes) {
        if (axes == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (int i = 0; i < axes.length; i++) {
            if (axes[i] == null || axes[i].getRange() == null) {
                continue;
            }

            back.append(i).append(',');

            Range range = axes[i].getRange();
            back.append(range.getStart()).append(',').append(range.getEnd()).append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence renderStyles(IChartAxis[] axes) {
        if (axes == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (int i = 0; i < axes.length; i++) {
            if (axes[i] == null || axes[i].getColor() == null
                    || axes[i].getFontSize() < 0 || axes[i].getAlignment() == null) {
                continue;
            }

            back.append(i).append(',');
            back.append(render(axes[i].getColor())).append(',');
            back.append(axes[i].getFontSize()).append(',');
            back.append(render(axes[i].getAlignment())).append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence render(IChartGrid grid) {
        if (grid == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        back.append(grid.getXStepSize()).append(',');
        back.append(grid.getYStepSize());

        if (grid.getSegmentLength() >= 0) {
            back.append(',').append(grid.getSegmentLength());
            back.append(',').append(grid.getBlankLength());
        }

        return back;
    }

    private CharSequence render(IShapeMarker[] markers) {
        if (markers == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (IShapeMarker marker : markers) {
            back.append(render(marker.getType())).append(',');
            back.append(render(marker.getColor())).append(',');
            back.append(marker.getIndex()).append(',');
            back.append(marker.getPoint()).append(',');
            back.append(marker.getSize()).append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence render(IRangeMarker[] markers) {
        if (markers == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (IRangeMarker marker : markers) {
            back.append(render(marker.getType())).append(',');
            back.append(render(marker.getColor())).append(',');
            back.append(0).append(',');
            back.append(marker.getStart()).append(',');
            back.append(marker.getEnd()).append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence render(IFillArea[] areas) {
        if (areas == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (IFillArea area : areas) {
            back.append(render(area.getType())).append(',');
            back.append(render(area.getColor())).append(',');
            back.append(area.getStartIndex()).append(',');
            back.append(area.getEndIndex()).append(',');
            back.append(0).append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    private CharSequence render(ILineStyle[] styles) {
        if (styles == null) {
            return null;
        }

        StringBuilder back = new StringBuilder();

        for (ILineStyle style : styles) {
            if (style == null) {
                back.append('|');
                continue;
            }

            back.append(style.getThickness()).append(',');
            back.append(style.getSegmentLength()).append(',');
            back.append(style.getBlankLength()).append('|');
        }

        if (back.length() > 0) {
            back.setLength(back.length() - 1);
        }

        return back;
    }

    @Override
    protected void onComponentTag(ComponentTag tag) {
        checkComponentTag(tag, "img");
        super.onComponentTag(tag);

        tag.put("src", constructURL());
    }
}
