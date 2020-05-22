# What to Watch Server

Server of the WtW application. Uses a postgres DB laoded with 
[Group Lens dataset](https://grouplens.org/datasets/movielens/) (We run it using the ml-latest-small.zip dataset specifically)
It is at the moment of writing this commit deployed [here](https://wfh-challenge.herokuapp.com/graphiql).

## API

The link given above redirects to the graphiql editor so you can play with the existing queries. If you want to get the first 
10 movies just structure the following query:

```
query {
 movies(first:10) {
    edges {
      node {
        id
        title
      }
      cursor
    }
  }
}
```

This will result in a result like this:
```
{
  "data": {
    "movies": {
      "edges": [
        {
          "node": {
            "id": 1,
            "title": "Toy Story"
          },
          "cursor": "MV8x"
        },
        {
          "node": {
            "id": 2,
            "title": "Jumanji"
          },
          "cursor": "Ml8y"
        },
        {
          "node": {
            "id": 3,
            "title": "Grumpier Old Men"
          },
          "cursor": "M18z"
        },
        {
          "node": {
            "id": 4,
            "title": "Waiting to Exhale"
          },
          "cursor": "NF80"
        },
        {
          "node": {
            "id": 5,
            "title": "Father of the Bride Part II"
          },
          "cursor": "NV81"
        },
        {
          "node": {
            "id": 6,
            "title": "Heat"
          },
          "cursor": "Nl82"
        },
        {
          "node": {
            "id": 7,
            "title": "Sabrina"
          },
          "cursor": "N183"
        },
        {
          "node": {
            "id": 8,
            "title": "Tom and Huck"
          },
          "cursor": "OF84"
        },
        {
          "node": {
            "id": 9,
            "title": "Sudden Death"
          },
          "cursor": "OV85"
        },
        {
          "node": {
            "id": 10,
            "title": "GoldenEye"
          },
          "cursor": "MTBfMTA="
        }
      ]
    }
  }
}
```

In order to obtain the next 10, just add the `after` parameter to the movies query. 
This will need to be the cursor of last element of the page (you can use another one but the results will have some 
elements of the page currently displaying):

```
query {
 movies(first:10, after:"MTBfMTA=") {
    edges {
      node {
        id
        title
      }
      cursor
    }
  }
}
```

If you wanted to filter by genres or title you add the corresponding values:

```
query {
 movies(first:10, title:"Hellboy", genres:[{
  id:1374389534720
  name:"Horror"
}]) {
    edges {
      node {
        id
        title
        genres {
          id
          name
        }
      }
      cursor
    }
  }
}
```
To get the genres(id and name), you can use the `genres` query.
```
query {
  genres {
    id
    name
  }
}
```
Sorting is also supported. Just specify the field you want to use for the sort like this:
```
query {
 movies(first:10, sortField:Budget, sortOrder:DESC) {
    edges {
      node {
        id
        title
        genres {
          id
          name
        }
      }
      cursor
    }
  }
}
```
**Note**: If you dont specify order it will be ASC by default

## Curl
You can access the api directly(without using graphiql) using the following endpoint:

`https://wfh-challenge.herokuapp.com/api/graphql`

It follows the graphql standards; you should be able to:
1. Use a `GET` with the variables and query in the query parameter
2. Use a `POST` with the body using the following format
```
{
  "query": "...",
  "operationName": "...",
  "variables": { "variableA": "someValue", ... }
}
```
You can review additional information on the [graphql docs about serving over http](https://graphql.org/learn/serving-over-http/)

## UI
The WtW-Front is deployed at time of writing on the following heroku link:
`https://what-to-watch-front.herokuapp.com/`