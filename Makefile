IMAGE="jloisel/jpetstore6:latest"

package:
	./mvnw clean package -DskipTests -DskipUpdateLicense
	mv target/jpetstore.war target/ROOT.war

image: package
	docker build --pull --rm=true -t $(IMAGE) .

run: image
	docker run -p 8080:8080 --rm $(IMAGE)

push: package
	docker buildx build --platform linux/amd64,linux/arm64 --provenance=false --sbom=false --pull -t $(IMAGE) --push .
