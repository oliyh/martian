run: install repl

install:
	@echo ":: Install dependencies"
	yarn install

repl:
	@echo ":: Start an interactive REPL"
	clj -M:fig

clean:
	@echo ":: Clean"
	rm -rf target/
	rm -rf resources/public/node_modules
	rm -rf resources/public/cljs-out

web: install
	mkdir -p resources/public/cljs-out
	@echo ":: Build Project"
	clojure -A:fig/simple
	@echo ":: Copy generated assets to resources-folder"
	cp target/public/cljs-out/dev-main.js resources/public/cljs-out
	@echo ":: Now open 'resources/public/index.html' to find the presentation"

outdated:
	@echo ":: Check for old dependencies"
	clojure -M:outdated
