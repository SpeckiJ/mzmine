/*
 * Copyright (c) 2004-2025 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.featdet_massdetection.wavelet;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.parameters.ParameterSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the Continuous Wavelet Transform (CWT), Mexican Hat, over raw datapoints of
 * a certain spectrum. After get the spectrum in the wavelet's time domain, we use the local maxima
 * to detect possible peaks in the original raw datapoints.
 */
public class FastWaveletMassDetector implements MassDetector {

  /**
   * Parameters of the wavelet, NPOINTS is the number of wavelet values to use The WAVELET_ESL &
   * WAVELET_ESL indicates the Effective Support boundaries
   */
  private static final int NPOINTS = 60000;
  private static final int WAVELET_ESL = -5;
  private static final int WAVELET_ESR = 5;

  private final double noiseLevel;
  private final int scaleLevel;
  private final double waveletWindow;
  private final int d = NPOINTS / (WAVELET_ESR - WAVELET_ESL);
  private final int HALF_NPOINTS = NPOINTS / 2;

  private boolean initialized;
  private int scaledESL;
  private int scaledESR;
  private double sqrtScaleLevel;
  private Wavelet wavelet;

  /**
   * required to create a default instance via reflection
   */
  public FastWaveletMassDetector() {
    this(0, 0, 0);
  }

  public FastWaveletMassDetector(final double noiseLevel, final int scaleLevel,
                                 final double waveletWindow) {
    this.noiseLevel = noiseLevel;
    this.scaleLevel = scaleLevel;
    // Avoid division by zero during calculations
    this.waveletWindow = (waveletWindow == 0.0) ? 1E-200 : waveletWindow;
  }

  @Override
  public MassDetector create(final ParameterSet params) {
    double noiseLevel = params.getValue(WaveletMassDetectorParameters.noiseLevel);
    int scaleLevel = params.getValue(WaveletMassDetectorParameters.scaleLevel);
    double waveletWindow = params.getValue(WaveletMassDetectorParameters.waveletWindow);
    return new FastWaveletMassDetector(noiseLevel, scaleLevel, waveletWindow);
  }

  @Override
  public boolean filtersActive() {
    return true; // profile to centroid always active
  }

  @Override
  public double[][] getMassValues(MassSpectrum scan) {
    double[][] raw = performCWT(scan);
    // System.out.println(Arrays.toString(raw[1]));
    return getMzPeaks(raw[0], raw[1], scan);
  }

  /**
   * Precomputed sampled wavelet.
   * Optimizes memory usage by only actually storing values of the sample that are > 0
   */
  private static class Wavelet {

    private final int leftBound;
    private final int rightBound;
    private double[] sparseValues;

    public Wavelet(double[] values) {
      // calculate bounds of the wavelet
      int i;
      for (i = 0; i < values.length; i++) {
        if (Double.compare(Math.abs(values[i]), 0) > 0) {
          break;
        }
      }
      leftBound = i;
      for (i = values.length - 1; i >= leftBound; i--) {
        if (Double.compare(Math.abs(values[i]), 0) > 0) {
          break;
        }
      }
      rightBound = i;

      // Copy actually relevant points to a new array and store it
      sparseValues = new double[rightBound - leftBound + 1];
      System.arraycopy(values, leftBound, sparseValues, 0, sparseValues.length);
    }

    public double val(int index) {
      return sparseValues[index - leftBound];
    }
  }

  /**
   * Precalculates the Wavelet and stores it in this instance.
   * As CWT fully depends on parameters set on instance creation this will never change during lifetime
   */
  private void initCWT() {
    if (!initialized) {
      double wstep = ((double) (WAVELET_ESR - WAVELET_ESL) / (double) NPOINTS);
      double[] W = new double[NPOINTS];

      double waveletIndex = WAVELET_ESL;
      for (int j = 0; j < NPOINTS; j++) {
        // precalculate the wavelet
        W[j] = cwtMEXHATreal(waveletIndex);
        waveletIndex += wstep;
      }

      wavelet = new Wavelet(W);
      scaledESL = scaleLevel * WAVELET_ESL;
      scaledESR = scaleLevel * WAVELET_ESR;
      sqrtScaleLevel = Math.sqrt(scaleLevel);
      initialized = true;
    }
  }

  /**
   * This function calculates the wavelets's coefficients in Time domain
   *
   * @param x Step of the wavelet
   */
  private double cwtMEXHATreal(double x) {
    /* c = 2 / ( sqrt(3) * pi^(1/4) ) */
    double c = 0.8673250705840776;

    x = x / waveletWindow;
    double x2 = x * x;
    return c * (1.0 - x2) * Math.exp(-x2 / 2);
  }

  /**
   * Perform the CWT over raw data points in the selected scale level
   *
   * @param scan MassSpectrum to be analyzed
   */
  private double[][] performCWT(MassSpectrum scan) {
    initCWT();
    int length = scan.getNumberOfDataPoints();
    double[] mzs = new double[length];
    double[] intensities = new double[length];

    final double dPerScale = (double) d / scaleLevel;

    final int leftBound = wavelet.leftBound;
    final int rightBound = wavelet.rightBound;

    for (int dx = 0; dx < length; dx++) {
      /*
       If the intensity is below the set noise-level we skip calculating the wavelet, it is very unlikely that after
       applying the wavelet this will become a peak
       */
      //TODO: check if this is allowed or we remove important points here
      double scanVal = scan.getIntensityValue(dx);
      if ((Double.compare(Math.abs(scanVal), noiseLevel) < 0)) {
        mzs[dx] = scan.getMzValue(dx);
        intensities[dx] = 0;
        continue;
      }

      /* Compute wavelet boundaries */
      int t1 = scaledESL + dx;
      if (t1 < 0) {
        t1 = 0;
      }
      int t2 = scaledESR + dx;
      if (t2 >= length) {
        t2 = (length - 1);
      }

      /* Perform convolution */
      final double baseOffset = HALF_NPOINTS - (dPerScale * dx);
      double intensity = 0.0;
      for (int i = t1; i <= t2; i++) {
        int ind = (int) (baseOffset + (dPerScale * i));
        if (ind <= leftBound || ind >= rightBound) {
          // Our wavelet is always = 0 outside the bounds
          continue;
        }

        double wind = wavelet.val(ind);
        intensity += scan.getIntensityValue(i) * wind;
      }
      intensity /= sqrtScaleLevel;
      // Eliminate the negative part of the wavelet map
      if (intensity < 0) {
        intensity = 0;
      }
      mzs[dx] = scan.getMzValue(dx);
      intensities[dx] = intensity;
    }
    return new double[][]{mzs, intensities};
  }

  /**
   * This function searches for maximums from wavelet data points
   */
  private double[][] getMzPeaks(double[] mzs, double[] intensities, MassSpectrum scan) {

    List<Double> detectedMzList = new ArrayList<>(1000);
    List<Double> detectedIntensityList = new ArrayList<>(1000);
    int peakMaxInd = 0;
    double peakMaxValWavelet = 0;
    double peakMaxValScan = 0;

    int index = 0;
    boolean insidePeak = false;

    // Iterate over all dataPoints and find connected areas where the intensity is > 0
    // For each area (referred to as "peak") calculate/approximate the actual intensity
    while (index < mzs.length - 1) {
      double value = intensities[index];
      if (value == 0 && insidePeak) {
        // We had a peak before, but intensity is now 0 --> peak is finished, add to output if it is not noise
        if (intensities[peakMaxInd] > noiseLevel) {
          detectedMzList.add(mzs[peakMaxInd]);
          detectedIntensityList.add(peakMaxValScan);
        }
        // Peak is finished, even if we did not add to output
        insidePeak = false;
      } else {
        // Intensity is > 0
        if (insidePeak) {
          // we have a current peak --> expand the current peak
          if (value > peakMaxValWavelet) {
            // we have a new maxValue
            peakMaxInd = index;
            peakMaxValWavelet = value;
          }
          // We track the highest value we saw in this peak here
          // this will later be used as the final intensity
          double scanValue = scan.getIntensityValue(index);
          if (scanValue > peakMaxValScan) {
            peakMaxValScan = scanValue;
          }
        } else {
          // we have no peak yet --> start a new
          peakMaxValWavelet = value;
          peakMaxValScan = -1;
          peakMaxInd = index;
          insidePeak = true;
        }
      }

      index++;
    }

    // Check that we finished
    if (insidePeak) {
      if (intensities[peakMaxInd] > noiseLevel) {
        detectedMzList.add(mzs[peakMaxInd]);
        detectedIntensityList.add(peakMaxValScan);
      }
    }

    // Transform into our desired output format
    double[] detectedMzs = new double[detectedIntensityList.size()];
    double[] detectedIntensities = new double[detectedIntensityList.size()];
    for (int i = 0; i < detectedMzs.length; i++) {
      detectedMzs[i] = detectedMzList.get(i);
      detectedIntensities[i] = detectedIntensityList.get(i);
    }
    return new double[][]{detectedMzs, detectedIntensities};
  }

  @Override
  public @NotNull String getName() {
    return "Fast Wavelet transform";
  }

  @Override
  public @NotNull Class<? extends ParameterSet> getParameterSetClass() {
    return WaveletMassDetectorParameters.class;
  }
}