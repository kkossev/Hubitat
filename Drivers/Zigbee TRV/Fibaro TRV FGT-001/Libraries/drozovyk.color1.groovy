library (
    author: "dmytro.rozovyk",
    category: "utils",
    description: "Notification helpers",
    name: "color1",
    namespace: "drozovyk"   
)

@Field static float         fltRoundingFactor = 0.4999999999
@Field static List<String>  colorComponents = ["Warm white", "Cold white", "Red", "Green", "Blue", "Amber", "Cyan", "Purple"]

static float toFloat(value) {
    return (value as float) + fltRoundingFactor
}

static Short toShort(value) {
    return (value as Short)
}

static String getColorName(int hue, int saturation) {
    def name = "white"
    if (saturation > 0) {
        Short hue = toShort(toFloat(hue) * (360.0/100.0))
        if(hue < 16) name = "red"
        else if(hue < 46)  name = "orange"
        else if(hue < 76)  name = "yellow"
        else if(hue < 106) name = "chartreuse"
        else if(hue < 136) name = "green"
        else if(hue < 166) name = "spring"
        else if(hue < 196) name = "cyan"
        else if(hue < 226) name = "azure"
        else if(hue < 256) name = "blue"
        else if(hue < 286) name = "violet"
        else if(hue < 316) name = "magenta"
        else if(hue < 346) name = "rose"
        else name = "red"
    }

    return name
}

static String getColorName(colormap) {
    return getColorName(colormap.hue as int, colormap.saturation as int);
}

static String getColorName(List colormap) {
    return getColorName(colormap[0].round(0) as int, colormap[1].round(0) as int)
}

@Field static colorTemperatureTableRGBHS = [ // V is always 100
    (1000): [toFloat(255), toFloat( 56), toFloat(  0)],
    (1100): [toFloat(255), toFloat( 71), toFloat(  0)],
    (1200): [toFloat(255), toFloat( 83), toFloat(  0)],
    (1300): [toFloat(255), toFloat( 93), toFloat(  0)],
    (1400): [toFloat(255), toFloat(101), toFloat(  0)],
    (1500): [toFloat(255), toFloat(109), toFloat(  0)],
    (1600): [toFloat(255), toFloat(115), toFloat(  0)],
    (1700): [toFloat(255), toFloat(121), toFloat(  0)],
    (1800): [toFloat(255), toFloat(126), toFloat(  0)],
    (1900): [toFloat(255), toFloat(131), toFloat(  0)],
    (2000): [toFloat(255), toFloat(138), toFloat( 18), toFloat( 30), toFloat(93)],
    (2100): [toFloat(255), toFloat(142), toFloat( 33), toFloat( 29), toFloat(87)],
    (2200): [toFloat(255), toFloat(147), toFloat( 44), toFloat( 29), toFloat(83)],
    (2300): [toFloat(255), toFloat(152), toFloat( 54), toFloat( 29), toFloat(79)],
    (2400): [toFloat(255), toFloat(157), toFloat( 63), toFloat( 29), toFloat(75)],
    (2500): [toFloat(255), toFloat(161), toFloat( 72), toFloat( 29), toFloat(72)],
    (2600): [toFloat(255), toFloat(165), toFloat( 79), toFloat( 29), toFloat(69)],
    (2700): [toFloat(255), toFloat(169), toFloat( 87), toFloat( 29), toFloat(66)],
    (2800): [toFloat(255), toFloat(173), toFloat( 94), toFloat( 29), toFloat(63)],
    (2900): [toFloat(255), toFloat(177), toFloat(101), toFloat( 30), toFloat(60)],    
    (3000): [toFloat(255), toFloat(180), toFloat(107), toFloat( 30), toFloat(58)],
    (3100): [toFloat(255), toFloat(184), toFloat(114), toFloat( 30), toFloat(55)],
    (3200): [toFloat(255), toFloat(187), toFloat(120), toFloat( 30), toFloat(53)],
    (3300): [toFloat(255), toFloat(190), toFloat(126), toFloat( 30), toFloat(51)],
    (3400): [toFloat(255), toFloat(193), toFloat(132), toFloat( 30), toFloat(48)],
    (3500): [toFloat(255), toFloat(196), toFloat(137), toFloat( 30), toFloat(46)],
    (3600): [toFloat(255), toFloat(199), toFloat(143), toFloat( 30), toFloat(44)],
    (3700): [toFloat(255), toFloat(201), toFloat(148), toFloat( 30), toFloat(42)],    
    (3800): [toFloat(255), toFloat(204), toFloat(153), toFloat( 30), toFloat(40)],
    (3900): [toFloat(255), toFloat(206), toFloat(159), toFloat( 30), toFloat(38)],
    (4000): [toFloat(255), toFloat(209), toFloat(163), toFloat( 30), toFloat(36)],
    (4100): [toFloat(255), toFloat(211), toFloat(168), toFloat( 30), toFloat(34)],
    (4200): [toFloat(255), toFloat(213), toFloat(173), toFloat( 29), toFloat(32)],
    (4300): [toFloat(255), toFloat(215), toFloat(177), toFloat( 29), toFloat(31)],
    (4400): [toFloat(255), toFloat(217), toFloat(182), toFloat( 29), toFloat(29)],
    (4500): [toFloat(255), toFloat(219), toFloat(186), toFloat( 29), toFloat(27)],    
    (4600): [toFloat(255), toFloat(221), toFloat(190), toFloat( 29), toFloat(25)],
    (4700): [toFloat(255), toFloat(223), toFloat(194), toFloat( 29), toFloat(24)],
    (4800): [toFloat(255), toFloat(225), toFloat(198), toFloat( 28), toFloat(22)],
    (4900): [toFloat(255), toFloat(227), toFloat(202), toFloat( 28), toFloat(21)],
    (5000): [toFloat(255), toFloat(228), toFloat(206), toFloat( 27), toFloat(19)],
    (5100): [toFloat(255), toFloat(230), toFloat(210), toFloat( 27), toFloat(18)],
    (5200): [toFloat(255), toFloat(232), toFloat(213), toFloat( 27), toFloat(16)],
    (5300): [toFloat(255), toFloat(233), toFloat(217), toFloat( 25), toFloat(15)],    
    (5400): [toFloat(255), toFloat(235), toFloat(220), toFloat( 26), toFloat(14)],
    (5500): [toFloat(255), toFloat(236), toFloat(224), toFloat( 23), toFloat(12)],
    (5600): [toFloat(255), toFloat(238), toFloat(227), toFloat( 24), toFloat(11)],    
    (5700): [toFloat(255), toFloat(239), toFloat(230), toFloat( 22), toFloat(10)],
    (5800): [toFloat(255), toFloat(240), toFloat(233), toFloat( 19), toFloat( 9)],
    (5900): [toFloat(255), toFloat(242), toFloat(236), toFloat( 19), toFloat( 7)],
    (6000): [toFloat(255), toFloat(243), toFloat(239), toFloat( 15), toFloat( 6)],
    (6100): [toFloat(255), toFloat(244), toFloat(242), toFloat(  9), toFloat( 5)],    
    (6200): [toFloat(255), toFloat(245), toFloat(245), toFloat(  0), toFloat( 4)],
    (6300): [toFloat(255), toFloat(246), toFloat(247), toFloat(353), toFloat( 4)],
    (6400): [toFloat(255), toFloat(248), toFloat(251), toFloat(334), toFloat( 3)],
    (6500): [toFloat(255), toFloat(249), toFloat(253), toFloat(320), toFloat( 2)],
	(6600): [toFloat(254), toFloat(249), toFloat(255)],
    (6700): [toFloat(252), toFloat(247), toFloat(255)],
    (6800): [toFloat(249), toFloat(246), toFloat(255)],
    (6900): [toFloat(247), toFloat(245), toFloat(255)],
    (7000): [toFloat(245), toFloat(243), toFloat(255)],
    (7100): [toFloat(243), toFloat(242), toFloat(255)],
    (7200): [toFloat(240), toFloat(241), toFloat(255)],
    (7300): [toFloat(239), toFloat(240), toFloat(255)],
    (7400): [toFloat(237), toFloat(239), toFloat(255)],
    (7500): [toFloat(235), toFloat(238), toFloat(255)],
    (7600): [toFloat(233), toFloat(237), toFloat(255)],
    (7700): [toFloat(231), toFloat(236), toFloat(255)],
    (7800): [toFloat(230), toFloat(235), toFloat(255)],
    (7900): [toFloat(228), toFloat(234), toFloat(255)],
    (8000): [toFloat(227), toFloat(233), toFloat(255)],
    (8100): [toFloat(225), toFloat(232), toFloat(255)],
    (8200): [toFloat(224), toFloat(231), toFloat(255)],
    (8300): [toFloat(222), toFloat(230), toFloat(255)],
    (8400): [toFloat(221), toFloat(230), toFloat(255)],
    (8500): [toFloat(220), toFloat(229), toFloat(255)],
    (8600): [toFloat(218), toFloat(229), toFloat(255)],
    (8700): [toFloat(217), toFloat(227), toFloat(255)],
    (8800): [toFloat(216), toFloat(227), toFloat(255)],
    (8900): [toFloat(215), toFloat(226), toFloat(255)],
    (9000): [toFloat(214), toFloat(225), toFloat(255)],
    (9100): [toFloat(212), toFloat(225), toFloat(255)],
    (9200): [toFloat(211), toFloat(224), toFloat(255)],
    (9300): [toFloat(210), toFloat(223), toFloat(255)],
    (9400): [toFloat(209), toFloat(223), toFloat(255)],
    (9500): [toFloat(208), toFloat(222), toFloat(255)],
    (9600): [toFloat(207), toFloat(221), toFloat(255)],
    (9700): [toFloat(207), toFloat(221), toFloat(255)],
    (9800): [toFloat(206), toFloat(220), toFloat(255)],
    (9900): [toFloat(205), toFloat(220), toFloat(255)],
   (10000): [toFloat(207), toFloat(218), toFloat(255)],
   (10100): [toFloat(207), toFloat(218), toFloat(255)],
   (10200): [toFloat(206), toFloat(217), toFloat(255)],
   (10300): [toFloat(205), toFloat(217), toFloat(255)],
   (10400): [toFloat(204), toFloat(216), toFloat(255)],
   (10500): [toFloat(204), toFloat(216), toFloat(255)],
   (10600): [toFloat(203), toFloat(215), toFloat(255)],
   (10700): [toFloat(202), toFloat(215), toFloat(255)],
   (10800): [toFloat(202), toFloat(214), toFloat(255)],
   (10900): [toFloat(201), toFloat(214), toFloat(255)],
   (11000): [toFloat(200), toFloat(213), toFloat(255)],
   (11100): [toFloat(200), toFloat(213), toFloat(255)],
   (11200): [toFloat(199), toFloat(212), toFloat(255)],
   (11300): [toFloat(198), toFloat(212), toFloat(255)],
   (11400): [toFloat(198), toFloat(212), toFloat(255)],
   (11500): [toFloat(197), toFloat(211), toFloat(255)],
   (11600): [toFloat(197), toFloat(211), toFloat(255)],
   (11700): [toFloat(197), toFloat(210), toFloat(255)],
   (11800): [toFloat(196), toFloat(210), toFloat(255)],
   (11900): [toFloat(195), toFloat(210), toFloat(255)],
   (12000): [toFloat(195), toFloat(209), toFloat(255)]
]

static def temperatureColorRGB(temp) {
    int   t = Math.min(Math.max(temp as int, 1000), 12000)
    int   tFrac = t % 100
    int   tFloor = t - tFrac
    int   tCeil = Math.min(tFloor + 100, 12000)

    def pointA = colorTemperatureTableRGBHS[tFloor]
    def pointB = colorTemperatureTableRGBHS[tCeil]
    float fB = toFloat(tFrac) / 100.0
    float fA = 1.0 - fB    
    
    return [(pointA[0] * fA + pointB[0] * fB) as Short, (pointA[1] * fA + pointB[1] * fB) as Short, (pointA[2] * fA + pointB[2] * fB) as Short]
}

static def changeWhiteBalance(color, originalWhiteColor, targetWhiteColor) {
    // should work on both floating point and integer numbers
    // should accept [0..1] and [0..255] ranges correctly
    float level = Math.max(Math.max((color[0] as float), (color[1] as float)), (color[2] as float))
    
    if(level > 0.0) {
        float r = ((color[0] as float) * (targetWhiteColor[0] as float)) / (originalWhiteColor[0] as float)
        float g = ((color[1] as float) * (targetWhiteColor[1] as float)) / (originalWhiteColor[1] as float)
        float b = ((color[2] as float) * (targetWhiteColor[2] as float)) / (originalWhiteColor[2] as float)

        if(r + g + b > 0.0) {
            float adj = level / Math.max(Math.max(r, g), b)
            return [r * adj, g * adj, b * adj];
        }
    }
    
    // black cannot be WP adjusted
    return color;    
}

// Always returns the maximum posible intensity (for both (RGB and W) 'white' color sources)
// Needs to know the white component color to adjust for
static def temperatureColorRGBW(temp, tempWhite = 6500) {
    def targetRGB = temperatureColorRGB(temp)
    def tempWhiteRGB = temperatureColorRGB(tempWhite)
    
    // Find a channel that needs the most of the intensity and scale target to fit
    float targetFactorMin = 2.0 // the biggest possible result factor (is twice as big as any full brightness color)
    targetRGB.eachWithIndex({
        denominator, index -> targetFactorMin = Math.min(targetFactorMin, (255.0 + toFloat(tempWhiteRGB[index])) / toFloat(denominator))
    })
    
    def resultRGB = []
    targetRGB.eachWithIndex({
        target, index -> resultRGB.add(
            toShort(targetFactorMin * toFloat(target)) - tempWhiteRGB[index]
        )
    })
    
    return resultRGB
}

static def temperatureColorRGB(temp, level) {
    def   color = temperatureColorRGB(temp)
    float scale = (level as float) / 100.0
    
    return [(color[0] * scale) as Short, (color[1] * scale) as Short, (color[2] * scale) as Short]
}

static def temperatureColorRGBW(temp, tempWhite, level) {
    def   color = temperatureColorRGBW(temp, tempWhite)
    float scale = (level as float) / 100.0
    
    return [(color[0] * scale) as Short, (color[1] * scale) as Short, (color[2] * scale) as Short, (255 * scale) as Short]
}

static String temperatureName(colormap) {
    def name = "white"

    if (colormap.saturation > 0) {
        float hue = toFloat(colormap.hue) * (360.0/100.0)
        def sat = colormap.saturation
        if(hue < 31) {
            if(sat > 87) {
                name = "candle"
            }
            else if(sat > 45) {
                // warm white
                name = "warm white"
                if(sat > 78) {
                    name += " (sodium lamp)"
                }
                else if(sat > 59) {
                    name += " (incadescent/LED/CFL lamp)"
                }
                else if(sat > 54) {
                    name += " (incadescent lamp)"            
                }
                else if(sat > 50) {
                    name += " (halogen lamp)"
                }
            }
            else if (sat > 14) {
                // neutral white
                name = "neutral white"
                if(sat < 39 && sat > 33) {
                    name += " (LED/CFL lamp)"
                }
                else if(sat < 34 && sat > 30) {
                    name += " (luminiscent lamp)"
                }
            }
        }
        else if (hue > 319 || hue < 26) {
            // cool white
            name = "cool white"
            if(sat < 10 && sat > 4) {
                name += " (luminiscent lamp)"
            }
            if(sat < 5 && sat > 2) {
                name += " (LED/luminiscent lamp)"
            }
        }
    }
    
    return name
}

static String getTemperatureName(temp) {
    def name = "white"

        float hue = toFloat(colormap.hue) * (360.0/100.0)
        def sat = colormap.saturation
        if(temp < 6300) {
            if(temp < 2100) {
                name = "candle"
            }
            else if(temp < 3600) {
                // warm white
                name = "warm white"
                if(temp < 2600) {
                    name += " (sodium lamp)"
                }
                else if(temp < 3000) {
                    name += " (incadescent/LED/CFL lamp)"
                }
                else if(temp < 3200) {
                    name += " (incadescent lamp)"            
                }
                else if(temp < 3400) {
                    name += " (halogen lamp)"
                }
            }
            else if (temp <= 5400) {
                // neutral white
                name = "neutral white"
                if(temp > 3800 && temp < 4200) {
                    name += " (LED/CFL lamp)"
                }
                else if(temp > 4200 && temp < 4400) {
                    name += " (luminiscent lamp)"
                }
            }
        }
        else if (temp > 6500 || temp < 5300) {
            // cool white
            name = "cool white"
            if(sat < 10 && sat > 4) {
                name += " (luminiscent lamp)"
            }
            if(sat < 5 && sat > 2) {
                name += " (LED/luminiscent lamp)"
            }
        }
    
    return name
}

static String temperatureName(List colormap) {
    return temperatureName([hue: colormap[0].round(0) as int, saturation: colormap[1].round(0) as int])
}

static def temperatureFactor(temp, tempWarm = 2000, tempCold = 6500) {
    // 1st convert target temp to RGB
    def targetRGB = temperatureColor(temp)
    
    // 2nd convert warm temp to RGB
    def warmRGB = temperatureColor(tempWarm)
    
    // 3rd convert cold temp to RGB
    def coldRGB = temperatureColor(tempCold)
    
    // 4th solve linear equation (find interpolation factor)
    
    return [1, 1]
}
