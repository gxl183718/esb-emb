<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"  
                xmlns:gg="http://www.gao.xiao.com" 
                version="1.0">
    <xsl:output method="xml" encoding="UTF-8" indent="yes" />
    <xsl:param name="a" />
    <xsl:param name="b" />
    <xsl:template match="/">
        <gg:sayGodBye>
            <name>
                <xsl:value-of select="$a" />
            </name>
            <age>
                <xsl:value-of select="$b" />
            </age>
        </gg:sayGodBye>
    </xsl:template>
</xsl:stylesheet>