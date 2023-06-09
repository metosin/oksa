# apollo-cljs-example

This is an example of using [Apollo GraphQL's React client](https://www.apollographql.com/docs/react/) in a ClojureScript browser application.

The example code is the result of following along the [react-apollo Getting Started guide](https://github.com/apollographql/apollo-client/blob/49fca2667986bd44f3ae5056853b6aed931156c8/docs/source/get-started.mdx).

It uses the following libraries & tools:
- [shadow-cljs](https://github.com/thheller/shadow-cljs) for compiling ClojureScript to JS
- [helix](https://github.com/Lokeh/helix/) for using and creating React components
- [cljs-bean](https://github.com/mfikes/cljs-bean) for interoping with JS objects easily
- [@apollo/react-hooks](https://www.apollographql.com/docs/react/api/react-hooks/)
- [apollo-boost](https://github.com/apollographql/apollo-client/tree/master/packages/apollo-boost)

## Development

```
git clone https://github.com/Lokeh/apollo-cljs-example.git

cd apollo-cljs-example

npm start
```

This will clone the repo and start the shadow-cljs build server.

Navigate to http://localhost:8700 to run the application.

## License

Copyright 2019 Will Acton. Licensed MIT
