# rapier-bom

The Rapier [BOM](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Bill_of_Materials_.28BOM.29_POMs) is the preferred method to manage Rapier versions.

To use this BOM, add it to your pom.xml:

    <properties>
      <rapier.version>x.y.z</rapier.version>
    </properties
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.aleph0</groupId>
                <artifactId>rapier-bom</artifactId>
                <version>${rapier.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
