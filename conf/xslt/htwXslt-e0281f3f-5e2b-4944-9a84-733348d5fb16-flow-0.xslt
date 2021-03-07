<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  
                xmlns:tempuri="http://tempuri.org/" 
                version="1.0">
    <xsl:output method="xml" encoding="UTF-8" indent="yes" />
    <xsl:param name="BLOWDOWN_MODE" />
    <xsl:template match="/">
        <tempuri:GetWaterList>
            <BLOWDOWN_MODE>
                <xsl:value-of select="$BLOWDOWN_MODE" />
            </BLOWDOWN_MODE>
        </tempuri:GetWaterList>
    </xsl:template>
</xsl:stylesheet>