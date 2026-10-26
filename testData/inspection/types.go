package smoke

import "time"

type PointerAlias = PackagePointer

type WrongTypes struct {
	Query   PointerAlias `query:"q"`
	Path    *int         `path:"id"`
	Header  *string      `header:"X-Name"`
	Form    *string      `form:"name"`
	Cookie  *string      `cookie:"session"`
	JSON    []int        `json:",string"`
	Minimum string       `minimum:"1"`
	Length  int          `minLength:"1"`
	Items   string       `minItems:"1"`
	BSON    int          `bson:",inline"`
	YAML    []string     `yaml:",inline"`
	Dynamo  []string     `dynamodbav:",numberset"`
	Unix    int          `dynamodbav:",unixtime"`
	Small   string       `bson:",minsize"`
}

type CorrectTypes struct {
	Query  string                 `query:"q"`
	Body   *struct{ Name string } `json:"body"`
	Length *string                `minLength:"1"`
	Items  []string               `minItems:"1"`
	Binary []byte                 `minLength:"1"`
	BSON   *struct{ ID string }   `bson:",inline"`
	YAML   map[string]string      `yaml:",inline"`
	Dynamo []int                  `dynamodbav:",numberset"`
	Unix   time.Time              `dynamodbav:",unixtime"`
	Small  int64                  `bson:",minsize"`
}

type GinCompatible struct {
	Form   *string `form:"name"`
	Header *string `header:"X-Name"`
}
