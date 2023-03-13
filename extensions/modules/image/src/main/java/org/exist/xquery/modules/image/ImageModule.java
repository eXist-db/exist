/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.image;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import javax.annotation.Nullable;

import static org.exist.xquery.FunctionDSL.functionDefs;

/**
 * eXist-db Image Module Extension.
 * <p>
 * An extension module for the eXist-db Native XML Database that allows operations
 * on images stored in the eXist-db database.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author ljo
 * @author <a href="mailto:rtroilo@gmail.com">Rafael Troilo</a>
 * @version 2.0.0
 * @serial 2023-03-10
 */
public class ImageModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/image";

    public final static String PREFIX = "image";
    public final static String INCLUSION_DATE = "2006-03-13";
    public final static String RELEASED_IN_VERSION = "eXist-1.2";

    private final static FunctionDef[] functions = functionDefs(
            functionDefs(GetWidthFunction.class, GetWidthFunction.signature),
            functionDefs(GetHeightFunction.class, GetHeightFunction.signature),
            functionDefs(ScaleFunction.class, ScaleFunction.FS_SCALE),
            functionDefs(GetThumbnailsFunction.class, GetThumbnailsFunction.signature),
            functionDefs(CropFunction.class, CropFunction.signature)
    );

    public ImageModule(Map<String, List<?>> parameters) {
        super(functions, parameters);
    }

    static final RenderingHintVariable[] RENDERING_HINT_VARIABLES = {
            renderingHintVariable("alpha-interpolation", RenderingHints.KEY_ALPHA_INTERPOLATION),
            renderingHintVariable("alpha-interpolation_default", RenderingHints.VALUE_ALPHA_INTERPOLATION_DEFAULT),
            renderingHintVariable("alpha-interpolation_speed", RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED),
            renderingHintVariable("alpha-interpolation_quality", RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY),

            renderingHintVariable("antialiasing", RenderingHints.KEY_ANTIALIASING),
            renderingHintVariable("antialiasing_default", RenderingHints.VALUE_ANTIALIAS_DEFAULT),
            renderingHintVariable("antialiasing_on", RenderingHints.VALUE_ANTIALIAS_ON),
            renderingHintVariable("antialiasing_off", RenderingHints.VALUE_ANTIALIAS_OFF),

            renderingHintVariable("color-rendering", RenderingHints.KEY_COLOR_RENDERING),
            renderingHintVariable("color-rendering_default", RenderingHints.VALUE_COLOR_RENDER_DEFAULT),
            renderingHintVariable("color-rendering_speed", RenderingHints.VALUE_COLOR_RENDER_SPEED),
            renderingHintVariable("color-rendering_quality", RenderingHints.VALUE_COLOR_RENDER_QUALITY),

            renderingHintVariable("dithering", RenderingHints.KEY_DITHERING),
            renderingHintVariable("dithering_default", RenderingHints.VALUE_DITHER_DEFAULT),
            renderingHintVariable("dithering_disable", RenderingHints.VALUE_DITHER_DISABLE),
            renderingHintVariable("dithering_enable", RenderingHints.VALUE_DITHER_ENABLE),

            renderingHintVariable("fractional-metrics", RenderingHints.KEY_FRACTIONALMETRICS),
            renderingHintVariable("fractional-metrics_default", RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT),
            renderingHintVariable("fractional-metrics_off", RenderingHints.VALUE_FRACTIONALMETRICS_OFF),
            renderingHintVariable("fractional-metrics_on", RenderingHints.VALUE_FRACTIONALMETRICS_ON),

            renderingHintVariable("interpolation", RenderingHints.KEY_INTERPOLATION),
            renderingHintVariable("interpolation_nearest_neighbor", RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR),
            renderingHintVariable("interpolation_bilinear", RenderingHints.VALUE_INTERPOLATION_BILINEAR),
            renderingHintVariable("interpolation_bicubic", RenderingHints.VALUE_INTERPOLATION_BICUBIC),

            renderingHintVariable("rendering", RenderingHints.KEY_RENDERING),
            renderingHintVariable("rendering_default", RenderingHints.VALUE_RENDER_DEFAULT),
            renderingHintVariable("rendering_speed", RenderingHints.VALUE_RENDER_SPEED),
            renderingHintVariable("rendering_quality", RenderingHints.VALUE_RENDER_QUALITY),

            renderingHintVariable("resolution-variant", RenderingHints.KEY_RESOLUTION_VARIANT),
            renderingHintVariable("resolution-variant_default", RenderingHints.VALUE_RESOLUTION_VARIANT_DEFAULT),
            renderingHintVariable("resolution-variant_base", RenderingHints.VALUE_RESOLUTION_VARIANT_BASE),
            renderingHintVariable("resolution-variant_size_fit", RenderingHints.VALUE_RESOLUTION_VARIANT_SIZE_FIT),
            renderingHintVariable("resolution-variant_dpi_fit", RenderingHints.VALUE_RESOLUTION_VARIANT_DPI_FIT),

            renderingHintVariable("stroke-control", RenderingHints.KEY_STROKE_CONTROL),
            renderingHintVariable("stroke-control_default", RenderingHints.VALUE_STROKE_DEFAULT),
            renderingHintVariable("stroke-control_normalize", RenderingHints.VALUE_STROKE_NORMALIZE),
            renderingHintVariable("stroke-control_pure", RenderingHints.VALUE_STROKE_PURE),

            renderingHintVariable("text-antialiasing", RenderingHints.KEY_TEXT_ANTIALIASING),
            renderingHintVariable("text-antialiasing_default", RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT),
            renderingHintVariable("text-antialiasing_on", RenderingHints.VALUE_TEXT_ANTIALIAS_ON),
            renderingHintVariable("text-antialiasing_off", RenderingHints.VALUE_TEXT_ANTIALIAS_OFF),
            renderingHintVariable("text-antialiasing_gasp", RenderingHints.VALUE_TEXT_ANTIALIAS_GASP),
            renderingHintVariable("text-antialiasing_lcd_hrgb", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB),
            renderingHintVariable("text-antialiasing_lcd_hbgr", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR),
            renderingHintVariable("text-antialiasing_lcd_vrgb", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB),
            renderingHintVariable("text-antialiasing_lcd_vbgr", RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR),

            renderingHintVariable("text-lcd-contrast", RenderingHints.KEY_TEXT_LCD_CONTRAST)
    };

    @Override
    public void prepare(final XQueryContext context) throws XPathException {
        // register the rendering hint variables
        for (final RenderingHintVariable renderingHintVariable : RENDERING_HINT_VARIABLES) {
            declareVariable(renderingHintVariable.name, renderingHintVariable.value);
        }
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "A module for performing operations on Images stored in the eXist db";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    /**
     * Create a thumbnail.
     *
     * @param image          the image
     * @param height         the height of the thumbnail
     * @param width          the width of the thumbnail
     * @param renderingHints hints for rendering
     * @return the thumbnail
     */
    protected static BufferedImage createThumb(final Image image, final int height, final int width, @Nullable Map<RenderingHints.Key, Object> renderingHints) {
        int thumbWidth = 0;
        int thumbHeight = 0;
        double scaleFactor = 0.0;

        int imageHeight = image.getHeight(null);
        int imageWidth = image.getWidth(null);

        if (imageHeight >= imageWidth) {
            scaleFactor = (double) height / (double) imageHeight;
            thumbWidth = (int) (imageWidth * scaleFactor);
            thumbHeight = height;
            if (thumbWidth > width) { // thumbwidth is greater than
                // maxThumbWidth, so we have to scale
                // again
                scaleFactor = (double) width / (double) thumbWidth;
                thumbHeight = (int) (thumbHeight * scaleFactor);
                thumbWidth = width;
            }
        } else {
            scaleFactor = (double) width / (double) imageWidth;
            thumbHeight = (int) (imageHeight * scaleFactor);
            thumbWidth = width;
            if (thumbHeight > height) { // thumbHeight is greater than
                // maxThumbHeight, so we have to scale
                // again
                scaleFactor = (double) height / (double) thumbHeight;
                thumbWidth = (int) (thumbWidth * scaleFactor);
                thumbHeight = height;
            }
        }

        final BufferedImage thumbImage = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = null;
        try {
            graphics2D = thumbImage.createGraphics();

            if (renderingHints == null) {
                // default to Bilinear Interpolation
                renderingHints = Collections.singletonMap(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }

            // set any rendering hints
            for (final Map.Entry<RenderingHints.Key, Object> renderingHint : renderingHints.entrySet()) {
                graphics2D.setRenderingHint(renderingHint.getKey(), renderingHint.getValue());
            }

            // draw the scaled image
            graphics2D.drawImage(image, 0, 0, thumbWidth, thumbHeight, null);
        } finally {
            if (graphics2D != null) {
                graphics2D.dispose();
            }
        }

        return thumbImage;
    }

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, paramTypes);
    }

    static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
        return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, variableParamTypes);
    }

    static RenderingHintVariable renderingHintVariable(final String localName, final Object value) {
        return new RenderingHintVariable(localName, value);
    }

    static class RenderingHintVariable {
        final QName name;
        final Object value;

        RenderingHintVariable(final String localName, final Object value) {
            this.name = new QName(localName, NAMESPACE_URI, PREFIX);
            this.value = value;
        }
    }
}
