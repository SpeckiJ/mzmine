package io.github.mzmine.modules.dataprocessing.featdet_massdetection.wavelet;

import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.impl.SimpleMassSpectrum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

class FastWaveletMassDetectorTest {


  double[] reference = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,0.0, 0.0, 0.0, 7847.57470703125, 16301.017578125, 22392.82421875, 24135.9296875, 21073.037109375, 12960.072265625, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 35990.56640625, 110519.8671875, 193602.46875, 232995.03125, 201051.890625, 122755.09375, 50061.671875, 13495.568359375, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 5488.59912109375, 55795.88671875, 156726.5625, 268974.0, 321311.15625, 273952.75, 159814.03125, 53259.20703125, 6549.82958984375, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 11277.009765625, 19345.1796875, 21468.6015625, 15693.56640625, 5078.52880859375, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10779.365234375, 17528.408203125, 20878.953125, 21130.1328125, 21889.5859375, 24567.669921875, 24938.138671875, 20557.9375, 13863.724609375, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 9379.205078125, 17017.576171875, 21187.771484375, 18122.4375};

  @RepeatedTest(1)
  void correctness() {
    List<MassSpectrum> spectra = makeSomeScans(1);

    final double noise = 2_000;
    final int scaleLevel = 2;
    final double windowPercent = 10;

    FastWaveletMassDetector fastWaveletMassDetector = new FastWaveletMassDetector(noise, scaleLevel, windowPercent);
    WaveletMassDetector waveletMassDetector = new WaveletMassDetector(noise, scaleLevel, windowPercent);


    for (MassSpectrum spectrum : spectra) {
      double[][] massValues = fastWaveletMassDetector.getMassValues(spectrum);
      double[][] reference = waveletMassDetector.getMassValues(spectrum);

      System.out.println(Arrays.deepToString(reference));
      System.out.println("----");
      System.out.println(Arrays.deepToString(massValues));

      Assertions.assertEquals(reference.length, massValues.length);
      Assertions.assertArrayEquals(reference[0], massValues[0]);
      Assertions.assertArrayEquals(massValues[1], reference[0]);

    }
  }

  /**
   * Generates mobility scans with a random number of data points (0-200), m/z values (0-1) and
   * intensities (0-1)
   *
   * @param numScans The number of moiblity scans.
   * @return The mobility scans.
   */
  private List<MassSpectrum> makeSomeScans(int numScans) {
    Random rnd = new Random(System.currentTimeMillis());
    List<MassSpectrum> scans = new ArrayList<>();

    for (int i = 0; i < numScans; i++) {
      int numDataPoints = (int) (rnd.nextFloat() * 10000);
      double[] mzs = new double[numDataPoints];
      double[] intensities = new double[numDataPoints];
      double mz = rnd.nextDouble();
      for (int j = 0; j < numDataPoints; j++) {
        mz += rnd.nextDouble();
        mzs[j] = mz;
        intensities[j] = rnd.nextDouble() * reference[j % reference.length];
      }
      scans.add(new SimpleMassSpectrum(mzs, intensities, MassSpectrumType.PROFILE));
    }

    return scans;
  }

}