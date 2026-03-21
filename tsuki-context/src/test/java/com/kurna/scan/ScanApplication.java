package com.kurna.scan;

import com.kurna.imported.LocalDateConfiguration;
import com.kurna.imported.ZonedDateConfiguration;
import com.kurna.tsuki.annotation.ComponentScan;
import com.kurna.tsuki.annotation.Import;

@ComponentScan
@Import({ LocalDateConfiguration.class, ZonedDateConfiguration.class })
public class ScanApplication {

}
