<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  
                xmlns:gao="http://www.gao.xiao.com" 
                version="1.0">
    <xsl:output method="xml" encoding="UTF-8" indent="yes" />
    <xsl:param name="name" />
    <xsl:param name="age" />
    <xsl:template match="/">
        <gao:sayGodBye>
            <name>
                <xsl:value-of select="$name" />
            </name>
            <age>
                <xsl:value-of select="$age" />
            </age>
        </gao:sayGodBye>
    </xsl:template>
</xsl:stylesheet>