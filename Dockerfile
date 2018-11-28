FROM maven:3-jdk-8

RUN mkdir -p /app/nde-termennetwerk
ADD conf /app/nde-termennetwerk/conf
ADD pom.xml /app/nde-termennetwerk
ADD src /app/nde-termennetwerk/src

WORKDIR /app/nde-termennetwerk

RUN mvn package

EXPOSE 8080

ENTRYPOINT ["mvn","-Dexec.args=-Dnde.config=/app/nde-termennetwerk/conf/termennetwerk.xml -classpath %classpath nl.knaw.huc.di.nde.Main", "-Dexec.executable=java", "org.codehaus.mojo:exec-maven-plugin:1.5.0:exec"]