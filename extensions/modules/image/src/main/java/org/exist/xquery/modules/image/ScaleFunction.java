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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.image.ImageModule.functionSignatures;

/**
 * eXist-db Image Module Extension ScaleFunction.
 * <p>
 * Scale's an Image.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author Loren Cahlander
 * @version 2.0.0
 * @serial 2023-03-10
 */
public class ScaleFunction extends BasicFunction {
    private static final Logger logger = LogManager.getLogger(ScaleFunction.class);

    private static final StringValue KEY_DESTINATION = new StringValue("destination");
    private static final StringValue KEY_MAX_HEIGHT = new StringValue("max-height");
    private static final StringValue KEY_MAX_WIDTH = new StringValue("max-width");
    private static final StringValue KEY_RENDERING_HINTS = new StringValue("rendering-hints");
    private static final StringValue KEY_SOURCE = new StringValue("source");
    private static final StringValue KEY_MEDIA_TYPE = new StringValue("media-type");

    private static final int MAXHEIGHT = 100;
    private static final int MAXWIDTH = 100;

    private static final String FS_SCALE_DESCRIPTION = "Scale the image image to a specified dimension.  If no dimensions are specified, then the default values are 'maxheight = 100' and 'maxwidth = 100'.";

    private static final String FS_SCALE_NAME = "scale";

    private static final FunctionParameterSequenceType FS_PARAM_IMAGE = param("image", Type.BASE64_BINARY, "The image data");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS = param("options", Type.MAP_ITEM, "A map of options for transforming the image. The map should be formatted like: map { 'source': map { 'media-type': xs:string }, 'destination': map { 'max-height': xs:integer?, 'max-width': xs:integer?, 'rendering-hints': map { $image:alpha-interpolation: ($image:alpha-interpolation_default|$image:alpha-interpolation_speed|$image:alpha-interpolation_default)?, $image:antialiasing: ($image:antialiasing_default|$image:antialiasing_on|$image:antialiasing_off)?, $image:color-rendering: ($image:color-rendering_default|$image:color-rendering_speed|$image:color-rendering_quality)?, $image:dithering: ($image:dithering_default|$image:dithering_enable|$image:dithering_disable)?, $image:fractional-metrics: ($image:fractional-metrics_default|$image:fractional-metrics_on|$image:fractional-metrics_off)?, $image:interpolation: ($image:interpolation_nearest_neighbor|$image:interpolation_bilinear|$image:interpolation_bicubic)?, $image:rendering: ($image:rendering_default|$image:rendering_speed|$image:rendering_quality)?, $image:resolution-variant: ($image:resolution-variant_default|$image:resolution-variant_base|$image:resolution-variant_size_fit|$image:resolution-variant_dpi_fit)?, $image:stroke-control: ($image:stroke-control_default|$image:stroke-control_normalize|$image:stroke-control_pure)?, $image:text-antialiasing: ($image:text-antialiasing_default|$image:text-antialiasing_on|$image:text-antialiasing_off|$image:text-antialiasing_gasp|$image:text-antialiasing_lcd_hrgb|$image:text-antialiasing_lcd_hbgr|$image:text-antialiasing_lcd_vrgb|$image:text-antialiasing_lcd_vbgr)? }?}");
    private static final FunctionParameterSequenceType FS_PARAM_DIMENSION = optManyParam("dimension", Type.INTEGER, "The maximum dimension of the scaled image. expressed in pixels (maxheight, maxwidth).  If empty, then the default values are 'maxheight = 100' and 'maxwidth = 100'.");
    private static final FunctionParameterSequenceType FS_PARAM_MIME_TYPE = param("media-type", Type.STRING, "The mime-type of the image");

    public static final FunctionSignature[] FS_SCALE = functionSignatures(
            FS_SCALE_NAME,
            FS_SCALE_DESCRIPTION,
            returnsOpt(Type.BASE64_BINARY, "the scaled image or an empty sequence if $image is invalid"),
            arities(
                    arity(FS_PARAM_IMAGE, FS_PARAM_OPTIONS),
                    arity(FS_PARAM_IMAGE, FS_PARAM_DIMENSION, FS_PARAM_MIME_TYPE)
            )
    );

    public ScaleFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * evaluate the call to the xquery scale() function,
     * it is really the main entry point of this class
     *
     * @param args            arguments from the scale() function call
     * @param contextSequence the Context Sequence to operate on (not used here internally!)
     * @return A sequence representing the result of the scale() function call
     */
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        /* 1. process the args */

        final BinaryValue imageParam = (BinaryValue) args[0].itemAt(0);

        int maxHeight = MAXHEIGHT;
        int maxWidth = MAXWIDTH;
        @Nullable String mediaType = null;
        @Nullable Map<RenderingHints.Key, Object> renderingHints = null;

        if (args.length == 2) {
            // called as image:scale#2
            final MapType optionsMap = (MapType) args[1].itemAt(0);

            if (optionsMap.contains(KEY_DESTINATION)) {
                final MapType destinationMap = (MapType) optionsMap.get(KEY_DESTINATION);

                // get the height and width
                if (destinationMap.contains(KEY_MAX_HEIGHT)) {
                    final IntegerValue maxHeightOption = (IntegerValue) destinationMap.get(KEY_MAX_HEIGHT);
                    maxHeight = maxHeightOption.getInt();
                }
                if (destinationMap.contains(KEY_MAX_WIDTH)) {
                    final IntegerValue maxWidthOption = (IntegerValue) destinationMap.get(KEY_MAX_WIDTH);
                    maxWidth = maxWidthOption.getInt();
                }

                // get the rendering options
                if (destinationMap.contains(KEY_RENDERING_HINTS)) {
                    final MapType renderingHintsMap = (MapType) destinationMap.get(KEY_RENDERING_HINTS);
                    for (final ImageModule.RenderingHintVariable renderingHintVariable : ImageModule.RENDERING_HINT_VARIABLES) {
                        if (renderingHintVariable.value instanceof RenderingHints.Key renderingHintKey) {
                            final JavaObjectValue mapKey = new JavaObjectValue(renderingHintKey);
                            if (renderingHintsMap.contains(mapKey)) {
                                final JavaObjectValue renderingHintValue = (JavaObjectValue) renderingHintsMap.get(mapKey);
                                if (renderingHints == null) {
                                    renderingHints = new HashMap<>();
                                }
                                renderingHints.put(renderingHintKey, renderingHintValue.getObject());
                            }
                        }
                    }
                }
            }

            if (optionsMap.contains(KEY_SOURCE)) {
                final MapType sourceMap = (MapType) optionsMap.get(KEY_SOURCE);
                // get the media-type
                if (sourceMap.contains(KEY_MEDIA_TYPE)) {
                        final StringValue mediaTypeOption = (StringValue) sourceMap.get(KEY_MEDIA_TYPE);
                        mediaType = mediaTypeOption.getStringValue();
                }
            }

            if (mediaType == null) {
                throw new XPathException(this, "source media-type must be provided in the options map");
            }


        } else {
            // called as image:scale#3

            // get the height and width
            if (!args[1].isEmpty()) {
                maxHeight = ((IntegerValue) args[1].itemAt(0)).getInt();
                if (args[1].hasMany()) {
                    maxWidth = ((IntegerValue) args[1].itemAt(1)).getInt();
                }
            }
            // get the media-type
            mediaType = args[2].itemAt(0).getStringValue();
        }


        /* 2. invoke the scaling */

        final String formatName = mediaType.substring(mediaType.indexOf("/") + 1);
        try (final InputStream inputStream = imageParam.getInputStream()) {

            final Image image = ImageIO.read(inputStream);
            if (image == null) {
                logger.error("Unable to read image data!");
                return Sequence.EMPTY_SEQUENCE;
            }

            //scale the image
            final BufferedImage bImage = ImageModule.createThumb(image, maxHeight, maxWidth, renderingHints);

            //get the new scaled image
            try (final UnsynchronizedByteArrayOutputStream os = UnsynchronizedByteArrayOutputStream.builder().get()) {
                ImageIO.write(bImage, formatName, os);

                //return the new scaled image data
                return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), os.toInputStream(), this);
            }
        } catch (final Exception e) {
            throw new XPathException(this, e.getMessage());
        }
    }
}
