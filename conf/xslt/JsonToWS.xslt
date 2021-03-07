<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  xmlns:gao="http://www.gao.xiao.com" version="1.0">
    <xsl:output method="xml" encoding="UTF-8" indent="yes" />
    <xsl:param name="params" />
    <xsl:template match="/">
        <gao:sayGodBye>
                <xsl:value-of select="$params" />
        </gao:sayGodBye>
    </xsl:template>
</xsl:stylesheet>