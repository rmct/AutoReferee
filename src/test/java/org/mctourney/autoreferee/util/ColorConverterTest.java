package org.mctourney.autoreferee.util;

import org.bukkit.Color;
import org.junit.Assert;
import org.junit.Test;

public class ColorConverterTest
{
	@Test
	public void testHexToColor() throws Exception
	{
		Assert.assertEquals(Color.fromRGB(255, 255, 255), ColorConverter.hexToColor("#ffffff"));
		Assert.assertEquals(Color.fromRGB(255,   0, 136), ColorConverter.hexToColor("#ff0088"));
		Assert.assertEquals(Color.fromRGB(222, 173,   0), ColorConverter.hexToColor("#dead00"));

		Assert.assertEquals(Color.fromRGB(160, 208,  48), ColorConverter.hexToColor("#ad3"));
		Assert.assertEquals(Color.fromRGB(192,  96, 192), ColorConverter.hexToColor("#c6c"));
		Assert.assertEquals(Color.fromRGB(  0, 112, 112), ColorConverter.hexToColor("#077"));
	}

	@Test
	public void testRgbToColor() throws Exception
	{
		Assert.assertEquals(Color.fromRGB(255, 255, 255), ColorConverter.rgbToColor("255,255,255"));
		Assert.assertEquals(Color.fromRGB(255,   0, 136), ColorConverter.rgbToColor("255,0,136"));
		Assert.assertEquals(Color.fromRGB(222, 173,   0), ColorConverter.rgbToColor("222,173,000"));

		Assert.assertEquals(Color.fromRGB(160, 208,  48), ColorConverter.rgbToColor("160/208/048"));
		Assert.assertEquals(Color.fromRGB(192,  96, 192), ColorConverter.rgbToColor("192  96 192"));
		Assert.assertEquals(Color.fromRGB(  0, 112, 112), ColorConverter.rgbToColor("000 112 112"));
	}
}
